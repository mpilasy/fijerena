package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton

/** One action in [ItemActionsDialog] besides the favourite one. */
@Immutable
data class ItemMenuAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * The phone's long-press menu on a title or channel (Search results, list rows): the item's name,
 * its other actions (mark watched, remove from Recent…) as text buttons, and Cancel. Every action
 * closes the menu. A null [onToggleFavorite] leaves the favourite action out (a Wanted row, which
 * is no source item).
 *
 * Removals are never the main button (UX audit fixes plan → UX-D1): Add to Favourites is the
 * confirm button, but Remove from Favourites is a plain action after the others, the last one above
 * Cancel. Callers put their own removals (Remove from Recent) at the end of [actions].
 */
@Composable
fun ItemActionsDialog(
    title: String,
    isFavorite: Boolean,
    onToggleFavorite: (() -> Unit)?,
    onDismiss: () -> Unit,
    actions: List<ItemMenuAction> = emptyList(),
) {
    val removeFavorite =
        if (isFavorite && onToggleFavorite != null) {
            ItemMenuAction(CinemaIcons.StarBorder, stringResource(R.string.favorite_remove), onToggleFavorite)
        } else {
            null
        }
    val listed = actions + listOfNotNull(removeFavorite)
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            CinemaText(text = title, role = TextRole.Heading, maxLines = 2)
        },
        text =
            listed.takeIf { it.isNotEmpty() }?.let {
                {
                    Column {
                        listed.forEach { action ->
                            CinemaDialogActionButton(
                                onClick = {
                                    action.onClick()
                                    onDismiss()
                                },
                                kind = ButtonKind.Other,
                            ) {
                                Icon(imageVector = action.icon, contentDescription = null)
                                Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                                CinemaText(action.label)
                            }
                        }
                    }
                }
            },
        confirmButton = {
            if (onToggleFavorite != null && !isFavorite) {
                CinemaDialogActionButton(
                    onClick = {
                        onToggleFavorite()
                        onDismiss()
                    },
                    kind = ButtonKind.Main,
                ) {
                    CinemaText(stringResource(R.string.favorite_add))
                }
            }
        },
        dismissButton = {
            CinemaDialogActionButton(onClick = onDismiss, kind = ButtonKind.Other) {
                CinemaText(stringResource(R.string.common_cancel))
            }
        },
    )
}
