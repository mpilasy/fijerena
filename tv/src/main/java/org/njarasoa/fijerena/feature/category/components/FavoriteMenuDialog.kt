package org.njarasoa.fijerena.feature.category.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.model.FavoriteMenuTarget
import org.njarasoa.fijerena.core.ui.model.nameAndFavoriteState
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import androidx.compose.foundation.layout.size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.tv.material3.Icon
import org.njarasoa.fijerena.core.ui.components.CinemaDialogActionButton
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.ui.components.input.TvInputListItem
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * Themed context menu dialog for a long-pressed category/stream: favorite toggle always, watched
 * toggle too when [onToggleWatched] is given — `target.isWatched == null` (Live TV, categories)
 * is what keeps callers from passing one. See docs/plans/20260828_watch-state-durable-storage-plan.md Phase 6.
 * Two independent actions rather than an AlertDialog's usual confirm/cancel pair: each row commits
 * immediately, `onDismiss` alone closes the menu.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FavoriteContextMenuDialog(
    target: FavoriteMenuTarget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onToggleWatched: (() -> Unit)? = null,
    onRemoveFromRecent: (() -> Unit)? = null,
) {
    val (itemName, isFavorite) = target.nameAndFavoriteState()
    val streamTarget = target as? FavoriteMenuTarget.Stream
    val isWatched = streamTarget?.isWatched
    val isInRecent = streamTarget?.isInRecent == true

    var showConfirmDialog by remember { mutableStateOf(false) }

    val favoriteActionText = if (isFavorite) stringResource(R.string.favorite_remove) else stringResource(R.string.favorite_add)
    val watchedActionText =
        if (isWatched == true) stringResource(R.string.watched_unmark) else stringResource(R.string.watched_mark)
    val recentActionText = stringResource(R.string.recent_remove)

    val cancelFocusRequester = remember { FocusRequester() }

    if (showConfirmDialog) {
        CinemaAlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = stringResource(R.string.favorite_remove_confirm_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = CinemaTextPrimary,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.favorite_remove_confirm_message, itemName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CinemaTextSecondary,
                )
            },
            confirmButton = {
                CinemaDialogActionButton(
                    onClick = {
                        onConfirm()
                        onDismiss()
                    },
                    colors =
                        androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = CinemaError,
                        ),
                ) {
                    Text(text = stringResource(R.string.favorite_remove))
                }
            },
            dismissButton = {
                CinemaDialogActionButton(
                    onClick = onDismiss,
                    colors =
                        androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = CinemaSurfaceVariant,
                            contentColor = CinemaTextPrimary,
                        ),
                ) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
            containerColor = CinemaSurface,
            titleContentColor = CinemaTextPrimary,
            textContentColor = CinemaTextSecondary,
        )
    } else {
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
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    if (onToggleWatched != null && isWatched != null) {
                        TvInputListItem(
                            selected = false,
                            onClick = {
                                onToggleWatched()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            leadingContent = {
                                Icon(
                                    imageVector = if (isWatched) CinemaIcons.RadioButtonUnchecked else CinemaIcons.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(TvDimensions.iconSmall),
                                    tint = CinemaAccent,
                                )
                            },
                            headlineContent = {
                                Text(
                                    text = watchedActionText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CinemaTextPrimary,
                                )
                            },
                        )
                    }

                    if (onRemoveFromRecent != null && isInRecent) {
                        TvInputListItem(
                            selected = false,
                            onClick = {
                                onRemoveFromRecent()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            leadingContent = {
                                Icon(
                                    imageVector = CinemaIcons.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(TvDimensions.iconSmall),
                                    tint = CinemaError,
                                )
                            },
                            headlineContent = {
                                Text(
                                    text = recentActionText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CinemaError,
                                )
                            },
                        )
                    }

                    TvInputListItem(
                        selected = false,
                        onClick = {
                            if (isFavorite) {
                                showConfirmDialog = true
                            } else {
                                onConfirm()
                                onDismiss()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        leadingContent = {
                            Icon(
                                imageVector = if (isFavorite) CinemaIcons.StarBorder else CinemaIcons.Star,
                                contentDescription = null,
                                modifier = Modifier.size(TvDimensions.iconSmall),
                                tint = if (isFavorite) CinemaError else CinemaAccent,
                            )
                        },
                        headlineContent = {
                            Text(
                                text = favoriteActionText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isFavorite) CinemaError else CinemaTextPrimary,
                            )
                        },
                    )

                    TvInputListItem(
                        selected = false,
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().focusRequester(cancelFocusRequester),
                        leadingContent = {
                            Icon(
                                imageVector = CinemaIcons.Close,
                                contentDescription = null,
                                modifier = Modifier.size(TvDimensions.iconSmall),
                                tint = CinemaTextSecondary,
                            )
                        },
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.common_cancel),
                                style = MaterialTheme.typography.bodyMedium,
                                color = CinemaTextSecondary,
                            )
                        },
                    )
                }
            },
            initialFocus = cancelFocusRequester,
            confirmButton = {},
            containerColor = CinemaSurface,
            titleContentColor = CinemaTextPrimary,
            textContentColor = CinemaTextSecondary,
        )
    }
}
