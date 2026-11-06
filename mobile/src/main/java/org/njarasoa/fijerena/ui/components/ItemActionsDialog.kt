package org.njarasoa.fijerena.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.MenuAction
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

/**
 * The phone's long-press menu on a title or channel (Search results, list rows): a
 * [CinemaActionMenu] with the item's name and the favourite action added to the caller's
 * [actions] — Add to Favourites first, Remove from Favourites last with the other removals. A null
 * [onToggleFavorite] leaves the favourite action out (a Wanted row, which is no source item).
 */
@Composable
fun ItemActionsDialog(
    title: String,
    isFavorite: Boolean,
    onToggleFavorite: (() -> Unit)?,
    onDismiss: () -> Unit,
    actions: List<MenuAction> = emptyList(),
    // Opens the favourite's tag dialog (favourite tags plan, FT-D7); shown on a favourite only.
    onEditTags: (() -> Unit)? = null,
) {
    val favorite =
        onToggleFavorite?.let { toggle ->
            if (isFavorite) {
                MenuAction(CinemaIcons.StarBorder, stringResource(R.string.favorite_remove), removal = true, onClick = toggle)
            } else {
                MenuAction(CinemaIcons.Star, stringResource(R.string.favorite_add), onClick = toggle)
            }
        }
    val tags = onEditTags?.takeIf { isFavorite }?.let { MenuAction(CinemaIcons.Tag, stringResource(R.string.favorite_tags), onClick = it) }
    CinemaActionMenu(
        title = title,
        actions = listOfNotNull(favorite, tags) + actions,
        onDismiss = onDismiss,
    )
}
