package org.njarasoa.fijerena.feature.category.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.model.FavoriteMenuTarget
import org.njarasoa.fijerena.core.ui.model.nameAndFavoriteState
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton

/**
 * Themed context menu dialog for favoriting categories/shows.
 * Uses AlertDialog with Cinema theme colors.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun FavoriteContextMenuDialog(
    target: FavoriteMenuTarget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (itemName, isFavorite) = target.nameAndFavoriteState()

    val actionText = if (isFavorite) stringResource(R.string.favorite_remove) else stringResource(R.string.favorite_add)

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = itemName,
                style = MaterialTheme.typography.titleMedium,
                color = CinemaTextPrimary,
                maxLines = 2,
            )
        },
        text = null,
        confirmButton = {
            CinemaPrimaryButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                text = actionText,
            )
        },
        dismissButton = {
            CinemaSecondaryButton(
                onClick = onDismiss,
                text = stringResource(R.string.common_cancel),
            )
        },
        containerColor = CinemaSurface,
        titleContentColor = CinemaTextPrimary,
        textContentColor = CinemaTextSecondary,
    )
}
