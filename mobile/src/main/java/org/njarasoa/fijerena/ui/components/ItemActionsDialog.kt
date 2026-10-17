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
 * its other actions (mark watched, remove from Recent…) as text buttons, Add to / Remove from
 * Favourites as the confirm button, and Cancel. Every action closes the menu.
 */
@Composable
fun ItemActionsDialog(
    title: String,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
    actions: List<ItemMenuAction> = emptyList(),
) {
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            CinemaText(text = title, role = TextRole.Heading, maxLines = 2)
        },
        text =
            actions.takeIf { it.isNotEmpty() }?.let {
                {
                    Column {
                        actions.forEach { action ->
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
            CinemaDialogActionButton(
                onClick = {
                    onToggleFavorite()
                    onDismiss()
                },
                kind = ButtonKind.Main,
            ) {
                CinemaText(stringResource(if (isFavorite) R.string.favorite_remove else R.string.favorite_add))
            }
        },
        dismissButton = {
            CinemaDialogActionButton(onClick = onDismiss, kind = ButtonKind.Other) {
                CinemaText(stringResource(R.string.common_cancel))
            }
        },
    )
}
