package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaIcon
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing

/** One action in [ItemActionsDialog] besides the favourite one. */
@Immutable
data class ItemMenuAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * The phone's long-press menu on a title or channel (Search results, list rows): the item's name,
 * then its actions as one list of rows of the same look (Add to Favourites first, the caller's
 * [actions], then Remove from Favourites), then Cancel. Every action closes the menu. A null
 * [onToggleFavorite] leaves the favourite action out (a Wanted row, which is no source item).
 *
 * Removals come last, just above Cancel (UX audit fixes plan → UX-D1; CONTRIBUTING rule 7): callers
 * put their own removals (Remove from Recent) at the end of [actions], and Remove from Favourites
 * follows them.
 */
@Composable
fun ItemActionsDialog(
    title: String,
    isFavorite: Boolean,
    onToggleFavorite: (() -> Unit)?,
    onDismiss: () -> Unit,
    actions: List<ItemMenuAction> = emptyList(),
) {
    val favorite =
        onToggleFavorite?.let { toggle ->
            if (isFavorite) {
                ItemMenuAction(CinemaIcons.StarBorder, stringResource(R.string.favorite_remove), toggle)
            } else {
                ItemMenuAction(CinemaIcons.Star, stringResource(R.string.favorite_add), toggle)
            }
        }
    val listed =
        if (isFavorite) {
            actions + listOfNotNull(favorite)
        } else {
            listOfNotNull(favorite) + actions
        }
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            CinemaText(text = title, role = TextRole.Heading, maxLines = 2)
        },
        text =
            listed.takeIf { it.isNotEmpty() }?.let {
                {
                    Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                        listed.forEach { action ->
                            CinemaChoiceRow(
                                onClick = {
                                    action.onClick()
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CinemaIcon(action.icon, contentDescription = null, tone = TextTone.Inherit)
                                    Spacer(modifier = Modifier.width(CinemaSpacing.sm))
                                    CinemaText(action.label, tone = TextTone.Inherit)
                                }
                            }
                        }
                    }
                }
            },
        cancel = DialogAction(stringResource(R.string.common_cancel), onClick = onDismiss),
    )
}
