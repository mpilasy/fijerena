package org.njarasoa.fijerena.feature.category.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.model.FavoriteMenuTarget
import org.njarasoa.fijerena.core.ui.model.nameAndFavoriteState
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.input.TvInputListItem
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * The list a row's menu was opened in, which decides the menu's first action
 * (docs/plans/20261009_tv-recents-favorites-plan.md → A): on Recent, Remove from Recent; on
 * Favourites, the favourite toggle (there always Remove from Favorites); elsewhere the favourite
 * toggle as before.
 */
enum class RowMenuList {
    OTHER,
    RECENT,
    FAVORITES,
}

/**
 * Themed context menu dialog for a category/stream row (long-press OK or the Menu key — UX
 * overhaul plan Part II P3): favorite toggle always, watched toggle too when [onToggleWatched] is
 * given — `target.isWatched == null` (Live TV, categories) is what keeps callers from passing one.
 * See docs/plans/archive/20260828_watch-state-durable-storage-plan.md Phase 6. Independent actions rather
 * than an AlertDialog's usual confirm/cancel pair: each row commits immediately, `onDismiss` alone
 * closes the menu. Focus opens on the first row: what the list is about ([list]) — so in Recent
 * the removal is first and focused, the user's choice over the "destructive never first" rule
 * (plan → A). Removals are not confirmed: the caller shows an Undo bar instead (`TvUndoBar`).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FavoriteContextMenuDialog(
    target: FavoriteMenuTarget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onToggleWatched: (() -> Unit)? = null,
    onRemoveFromRecent: (() -> Unit)? = null,
    list: RowMenuList = RowMenuList.OTHER,
) {
    val (itemName, isFavorite) = target.nameAndFavoriteState()
    val streamTarget = target as? FavoriteMenuTarget.Stream
    val isWatched = streamTarget?.isWatched
    val isInRecent = streamTarget?.isInRecent == true

    val favoriteActionText = if (isFavorite) stringResource(R.string.favorite_remove) else stringResource(R.string.favorite_add)
    val watchedActionText =
        if (isWatched == true) stringResource(R.string.watched_unmark) else stringResource(R.string.watched_mark)
    val recentActionText = stringResource(R.string.recent_remove)

    val firstActionFocusRequester = remember { FocusRequester() }
    val recentFirst = list == RowMenuList.RECENT && onRemoveFromRecent != null && isInRecent

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
                val removeFromRecentItem: @Composable (Modifier) -> Unit = { itemModifier ->
                    TvInputListItem(
                        selected = false,
                        onClick = {
                            onRemoveFromRecent?.invoke()
                            onDismiss()
                        },
                        modifier = itemModifier.fillMaxWidth(),
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

                if (recentFirst) removeFromRecentItem(Modifier.focusRequester(firstActionFocusRequester))

                TvInputListItem(
                    selected = false,
                    onClick = {
                        onConfirm()
                        onDismiss()
                    },
                    modifier =
                        Modifier.fillMaxWidth().then(
                            if (recentFirst) Modifier else Modifier.focusRequester(firstActionFocusRequester),
                        ),
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

                // Elsewhere the removal sits last, just above Cancel.
                if (!recentFirst && onRemoveFromRecent != null && isInRecent) removeFromRecentItem(Modifier)

                TvInputListItem(
                    selected = false,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
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
        initialFocus = firstActionFocusRequester,
        confirmButton = {},
        containerColor = CinemaSurface,
        titleContentColor = CinemaTextPrimary,
        textContentColor = CinemaTextSecondary,
    )
}
