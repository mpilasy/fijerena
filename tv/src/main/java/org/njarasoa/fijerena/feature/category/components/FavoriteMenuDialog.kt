package org.njarasoa.fijerena.feature.category.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
 * Themed context menu dialog for a long-pressed category/stream: favorite toggle always, watched
 * toggle too when [onToggleWatched] is given — `target.isWatched == null` (Live TV, categories)
 * is what keeps callers from passing one. See plans/watch-state-durable-storage-plan.md Phase 6.
 * Two independent actions rather than an AlertDialog's usual confirm/cancel pair: each row commits
 * immediately, `onDismiss` alone closes the menu.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun FavoriteContextMenuDialog(
    target: FavoriteMenuTarget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onToggleWatched: (() -> Unit)? = null,
) {
    val (itemName, isFavorite) = target.nameAndFavoriteState()
    val isWatched = (target as? FavoriteMenuTarget.Stream)?.isWatched

    val favoriteActionText = if (isFavorite) stringResource(R.string.favorite_remove) else stringResource(R.string.favorite_add)
    val watchedActionText =
        if (isWatched == true) stringResource(R.string.watched_unmark) else stringResource(R.string.watched_mark)

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
        text =
            if (onToggleWatched != null && isWatched != null) {
                {
                    Column {
                        CinemaPrimaryButton(
                            onClick = {
                                onToggleWatched()
                                onDismiss()
                            },
                            text = watchedActionText,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                null
            },
        confirmButton = {
            CinemaPrimaryButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                text = favoriteActionText,
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
