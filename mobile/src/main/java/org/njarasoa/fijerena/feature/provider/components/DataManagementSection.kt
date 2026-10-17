package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.player.domain.ProviderType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaDivider
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SyncState
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton

/** Library data: sync, last sync, totals and per-type counts. Clearing lives in [ProviderDangerZoneSection]. */
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
) {
    if (isEditMode) {
        Spacer(modifier = Modifier.height(CinemaSpacing.lg))

        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(CinemaSpacing.md)) {
                ProviderSectionTitle(
                    title = stringResource(R.string.provider_section_library_data),
                    subtitle = stringResource(R.string.provider_data_management_desc),
                )
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                cacheStats?.let { stats ->
                    // Sync Data Button (Xtream only)
                    if (selectedType == ProviderType.XTREAM) {
                        CinemaButton(
                            onClick = { viewModel.syncProvider(editId) },
                            enabled = !isBusy && syncState !is SyncState.Syncing,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            CinemaText(
                                if (syncState is SyncState.Syncing) {
                                    stringResource(
                                        R.string.provider_syncing,
                                    )
                                } else {
                                    stringResource(R.string.provider_sync_now_button)
                                },
                                role = TextRole.Label,
                                tone = TextTone.Inherit,
                            )
                        }

                        // Last Sync Stats
                        if ((currentProvider?.lastSyncedAtMs ?: 0L) > 0L) {
                            Spacer(modifier = Modifier.height(CinemaSpacing.xxs))
                            val time = NumberUtils.formatTimestamp(LocalContext.current, currentProvider?.lastSyncedAtMs ?: 0L)
                            val duration = NumberUtils.formatDuration(currentProvider?.lastSyncDurationMs ?: 0L)
                            CinemaText(
                                stringResource(R.string.provider_last_sync_status, time, duration),
                                role = TextRole.BodySmall,
                                tone = TextTone.Secondary,
                            )

                            // Only shown next to a clean sync: on error the columns hold the last
                            // *successful* sync's delta (ProviderDao.updateSyncStats COALESCEs
                            // rather than zeroing on failure), so showing it next to an error
                            // would misleadingly read as "this failed run found 5 changes".
                            if (currentProvider?.lastSyncError == null) {
                                val inserted = currentProvider?.lastSyncInserted ?: 0
                                val updated = currentProvider?.lastSyncUpdated ?: 0
                                val deleted = currentProvider?.lastSyncDeleted ?: 0
                                CinemaText(
                                    if (inserted == 0 && updated == 0 && deleted == 0) {
                                        stringResource(R.string.provider_sync_no_changes)
                                    } else {
                                        stringResource(R.string.provider_sync_delta, inserted, updated, deleted)
                                    },
                                    role = TextRole.BodySmall,
                                    tone = TextTone.Secondary,
                                )
                            }
                        }

                        if (syncState is SyncState.Error || (syncState is SyncState.Idle && currentProvider?.lastSyncError != null)) {
                            val errorMsg = (syncState as? SyncState.Error)?.message ?: currentProvider?.lastSyncError
                            if (errorMsg != null) {
                                CinemaText(errorMsg, role = TextRole.BodySmall, tone = TextTone.Danger)
                            }
                        }
                        if (syncState is SyncState.Success) {
                            CinemaText(stringResource(R.string.provider_sync_success), role = TextRole.BodySmall, tone = TextTone.Accent)
                        }
                        Spacer(modifier = Modifier.height(CinemaSpacing.md))
                    }

                    CinemaText(stringResource(R.string.provider_total_db_items_label))
                    val totalItems =
                        stats.liveTv.itemsCount + stats.movies.itemsCount + stats.tvShows.itemsCount
                    CinemaText(
                        stringResource(R.string.provider_total_items_value, NumberUtils.formatCount(totalItems)),
                        role = TextRole.Title,
                        tone = TextTone.Accent,
                    )

                    Spacer(modifier = Modifier.height(CinemaSpacing.md))
                    CinemaDivider()
                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                    CinemaText(stringResource(R.string.provider_live_tv_label), role = TextRole.Heading)
                    CinemaText(
                        stringResource(
                            R.string.provider_live_tv_stats,
                            NumberUtils.formatCount(stats.liveTv.categoryCount),
                            NumberUtils.formatCount(stats.liveTv.itemsCount),
                        ),
                        tone = TextTone.Accent,
                    )

                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                    CinemaText(stringResource(R.string.provider_movies_label), role = TextRole.Heading)
                    CinemaText(
                        stringResource(
                            R.string.provider_movies_stats,
                            NumberUtils.formatCount(stats.movies.categoryCount),
                            NumberUtils.formatCount(stats.movies.itemsCount),
                        ),
                        tone = TextTone.Accent,
                    )

                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                    CinemaText(stringResource(R.string.provider_tv_shows_label), role = TextRole.Heading)
                    CinemaText(
                        stringResource(
                            R.string.provider_tv_shows_stats,
                            NumberUtils.formatCount(stats.tvShows.categoryCount),
                            NumberUtils.formatCount(stats.tvShows.itemsCount),
                            NumberUtils.formatCount(stats.tvShows.episodesCount),
                        ),
                        tone = TextTone.Accent,
                    )
                }
            }
        }
    }
}

/**
 * Danger zone: every destructive action of this source, as outlined error-coloured buttons.
 * Each caller-provided callback opens that action's existing confirm dialog; per-type clears
 * are hidden while that type has nothing cached.
 */
@Composable
fun ColumnScope.ProviderDangerZoneSection(
    isEditMode: Boolean,
    cacheStats: XtreamRepository.CacheStats?,
    onClearFavorites: () -> Unit,
    onClearProgress: () -> Unit,
    onClearAllCache: () -> Unit,
    onClearLiveTvCache: () -> Unit,
    onClearMoviesCache: () -> Unit,
    onClearTvShowsCache: () -> Unit,
) {
    if (isEditMode) {
        Spacer(modifier = Modifier.height(CinemaSpacing.lg))

        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(CinemaSpacing.md)) {
                ProviderSectionTitle(
                    title = stringResource(R.string.provider_section_danger_zone),
                    titleTone = TextTone.Danger,
                )
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                CinemaText(stringResource(R.string.provider_clear_favorites_desc), role = TextRole.BodySmall, tone = TextTone.Secondary)
                Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                DangerButton(
                    onClick = onClearFavorites,
                    text = stringResource(R.string.provider_clear_favorites_button),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(CinemaSpacing.md))

                CinemaText(stringResource(R.string.provider_clear_progress_desc), role = TextRole.BodySmall, tone = TextTone.Secondary)
                Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                DangerButton(
                    onClick = onClearProgress,
                    text = stringResource(R.string.provider_clear_progress_button),
                    modifier = Modifier.fillMaxWidth(),
                )

                cacheStats?.let { stats ->
                    Spacer(modifier = Modifier.height(CinemaSpacing.md))

                    // Clear all cached library
                    CinemaText(stringResource(R.string.provider_data_management_desc), role = TextRole.BodySmall, tone = TextTone.Secondary)
                    Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                    DangerButton(
                        onClick = onClearAllCache,
                        text = stringResource(R.string.provider_clear_cached_library_button),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Per-type clears: only for types that have something to clear
                    if (stats.liveTv.itemsCount > 0) {
                        Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                        DangerRow(
                            label = stringResource(R.string.provider_live_tv_label),
                            onClear = onClearLiveTvCache,
                        )
                    }
                    if (stats.movies.itemsCount > 0) {
                        Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                        DangerRow(
                            label = stringResource(R.string.provider_movies_label),
                            onClear = onClearMoviesCache,
                        )
                    }
                    if (stats.tvShows.itemsCount > 0) {
                        Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                        DangerRow(
                            label = stringResource(R.string.provider_tv_shows_label),
                            onClear = onClearTvShowsCache,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DangerRow(
    label: String,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CinemaText(label, role = TextRole.Heading, modifier = Modifier.weight(1f))
        DangerButton(onClick = onClear, text = stringResource(R.string.provider_clear_button))
    }
}

/** Wipes this source's data ([ButtonKind.Danger]). */
@Composable
private fun DangerButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
) {
    CinemaButton(onClick = onClick, modifier = modifier, kind = ButtonKind.Danger) {
        CinemaText(text, role = TextRole.Label, tone = TextTone.Inherit)
    }
}
