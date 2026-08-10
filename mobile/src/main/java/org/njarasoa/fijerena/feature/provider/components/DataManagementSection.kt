package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.player.domain.ProviderType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SyncState
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.theme.CinemaError

@Composable
fun ColumnScope.DataManagementSection(
    isEditMode: Boolean,
    editId: Long,
    cacheStats: XtreamRepository.CacheStats?,
    selectedType: ProviderType,
    viewModel: ProviderViewModel,
    isBusy: Boolean,
    syncState: SyncState,
    currentProvider: ProviderEntity?,
    onShowClearCacheDialogChange: (Boolean) -> Unit,
    onShowClearLiveTvCacheDialogChange: (Boolean) -> Unit,
    onShowClearMoviesCacheDialogChange: (Boolean) -> Unit,
    onShowClearTvShowsCacheDialogChange: (Boolean) -> Unit,
) {
    if (isEditMode) {
        Spacer(modifier = Modifier.height(CinemaSpacing.lg))

        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(CinemaSpacing.md)) {
                Text(
                    text = stringResource(R.string.provider_data_management_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(CinemaSpacing.xxs))
                Text(
                    text = stringResource(R.string.provider_data_management_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                )
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                cacheStats?.let { stats ->
                    // Sync Data Button (Xtream only)
                    if (selectedType == ProviderType.XTREAM) {
                        CinemaButton(
                            onClick = { viewModel.syncProvider(editId) },
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (syncState is SyncState.Syncing) stringResource(R.string.provider_syncing) else stringResource(R.string.provider_sync_now_button))
                        }

                        // Last Sync Stats
                        if ((currentProvider?.lastSyncedAtMs ?: 0L) > 0L) {
                            Spacer(modifier = Modifier.height(CinemaSpacing.xxs))
                            val time = NumberUtils.formatTimestamp(LocalContext.current, currentProvider?.lastSyncedAtMs ?: 0L)
                            val duration = NumberUtils.formatDuration(currentProvider?.lastSyncDurationMs ?: 0L)
                            Text(
                                text = stringResource(R.string.provider_last_sync_status, time, duration),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
                            )
                        }

                        if (syncState is SyncState.Error || (syncState is SyncState.Idle && currentProvider?.lastSyncError != null)) {
                            val errorMsg = (syncState as? SyncState.Error)?.message ?: currentProvider?.lastSyncError
                            if (errorMsg != null) {
                                Text(
                                    text = errorMsg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CinemaError,
                                )
                            }
                        }
                        if (syncState is SyncState.Success) {
                            Text(
                                text = stringResource(R.string.provider_sync_success),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(modifier = Modifier.height(CinemaSpacing.md))
                    }

                    // Total Items
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.provider_total_db_items_label),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            val totalItems =
                                stats.liveTv.itemsCount + stats.movies.itemsCount + stats.tvShows.itemsCount
                            Text(
                                text = stringResource(R.string.provider_total_items_value, NumberUtils.formatCount(totalItems)),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        CinemaButton(
                            onClick = { onShowClearCacheDialogChange(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                        ) {
                            Text(stringResource(R.string.provider_clear_all_button))
                        }
                    }

                    Spacer(modifier = Modifier.height(CinemaSpacing.md))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.divider),
                    )
                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                    // Live TV
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.provider_live_tv_label), style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = stringResource(R.string.provider_live_tv_stats, NumberUtils.formatCount(stats.liveTv.categoryCount), NumberUtils.formatCount(stats.liveTv.itemsCount)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        CinemaOutlinedButton(
                            onClick = { onShowClearLiveTvCacheDialogChange(true) },
                            enabled = stats.liveTv.itemsCount > 0,
                        ) { Text(stringResource(R.string.provider_clear_button)) }
                    }

                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                    // Movies
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.provider_movies_label), style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = stringResource(R.string.provider_movies_stats, NumberUtils.formatCount(stats.movies.categoryCount), NumberUtils.formatCount(stats.movies.itemsCount)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        CinemaOutlinedButton(
                            onClick = { onShowClearMoviesCacheDialogChange(true) },
                            enabled = stats.movies.itemsCount > 0,
                        ) { Text(stringResource(R.string.provider_clear_button)) }
                    }

                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                    // TV Shows
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.provider_tv_shows_label), style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = stringResource(R.string.provider_tv_shows_stats, NumberUtils.formatCount(stats.tvShows.categoryCount), NumberUtils.formatCount(stats.tvShows.itemsCount), NumberUtils.formatCount(stats.tvShows.episodesCount)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        CinemaOutlinedButton(
                            onClick = { onShowClearTvShowsCacheDialogChange(true) },
                            enabled = stats.tvShows.itemsCount > 0,
                        ) { Text(stringResource(R.string.provider_clear_button)) }
                    }
                }
            } // Column
        } // GlassPanel
    }
}
