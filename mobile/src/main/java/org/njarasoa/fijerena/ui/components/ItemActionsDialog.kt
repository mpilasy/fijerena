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
) {
    val favorite =
        onToggleFavorite?.let { toggle ->
            if (isFavorite) {
                MenuAction(CinemaIcons.StarBorder, stringResource(R.string.favorite_remove), removal = true, onClick = toggle)
            } else {
                MenuAction(CinemaIcons.Star, stringResource(R.string.favorite_add), onClick = toggle)
            }
        }
    CinemaActionMenu(
        title = title,
        actions = listOfNotNull(favorite) + actions,
        onDismiss = onDismiss,
    )
}
