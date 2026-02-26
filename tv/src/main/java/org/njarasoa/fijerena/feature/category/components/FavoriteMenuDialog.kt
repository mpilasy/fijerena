package org.njarasoa.fijerena.feature.category.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.tv.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.CornerRadius

/**
 * Data class representing a pending favorite action from a long-press.
 */
internal sealed class FavoriteMenuTarget {
    data class Category(
        val categoryId: String,
        val categoryName: String,
        val contentType: String,
        val isFavorite: Boolean
    ) : FavoriteMenuTarget()

    data class Stream(
        val itemId: String,
        val itemName: String,
        val categoryId: String,
        val contentType: String,
        val isFavorite: Boolean
    ) : FavoriteMenuTarget()
}

/**
 * Themed context menu dialog for favoriting categories/shows.
 * Uses AlertDialog with Cinema theme colors.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun FavoriteContextMenuDialog(
    target: FavoriteMenuTarget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val (itemName, isFavorite) = when (target) {
        is FavoriteMenuTarget.Category -> target.categoryName to target.isFavorite
        is FavoriteMenuTarget.Stream -> target.itemName to target.isFavorite
    }

    val actionText = if (isFavorite) "Remove from Favorites" else "Add to Favorites"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = itemName,
                style = MaterialTheme.typography.titleMedium,
                color = CinemaTextPrimary,
                maxLines = 2
            )
        },
        text = null,
        confirmButton = {
            CinemaPrimaryButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                text = actionText
            )
        },
        dismissButton = {
            CinemaSecondaryButton(
                onClick = onDismiss,
                text = "Cancel"
            )
        },
        containerColor = CinemaSurface,
        titleContentColor = CinemaTextPrimary,
        textContentColor = CinemaTextSecondary,
        shape = RoundedCornerShape(CornerRadius.large)
    )
}
