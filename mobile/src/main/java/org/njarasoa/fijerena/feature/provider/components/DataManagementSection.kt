package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.player.diagnostics.Redact
import org.njarasoa.fijerena.core.player.domain.ProviderType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaDivider
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.MenuAction
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.home.syncDeltaText
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.utils.storedErrorText
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SyncState
import org.njarasoa.fijerena.feature.settings.components.SettingsListRow
import org.njarasoa.fijerena.ui.components.CinemaActionMenu
import org.njarasoa.fijerena.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton

/**
 * Library data: sync, the last successful sync, the last failure (its time, reason and the failures
 * since the last success), totals and per-type counts. Clearing lives in [ProviderDangerZoneSection].
 */
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

                        Spacer(modifier = Modifier.height(CinemaSpacing.xxs))
                        if ((currentProvider?.lastSyncedAtMs ?: 0L) <= 0L) {
                            CinemaText(
                                stringResource(R.string.provider_last_sync_never),
                                role = TextRole.BodySmall,
                                tone = TextTone.Secondary,
                            )
                        } else {
                            val time = NumberUtils.formatTimestamp(LocalContext.current, currentProvider?.lastSyncedAtMs ?: 0L)
                            val duration = NumberUtils.formatDuration(LocalResources.current, currentProvider?.lastSyncDurationMs ?: 0L)
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
                                        syncDeltaText(inserted, updated, deleted)
                                    },
                                    role = TextRole.BodySmall,
                                    tone = TextTone.Secondary,
                                )
                            }
                        }

                        if (syncState is SyncState.Error || (syncState is SyncState.Idle && currentProvider?.lastSyncError != null)) {
                            val errorMsg =
                                ((syncState as? SyncState.Error)?.message ?: currentProvider?.lastSyncError?.let { storedErrorText(it) })
                                    ?.let(Redact::text)
                            val storedError = currentProvider?.lastSyncError
                            val failedAt = currentProvider?.lastFailedAtMs
                            if (storedError != null && failedAt != null) {
                                CinemaText(
                                    stringResource(
                                        R.string.provider_last_failure_status,
                                        NumberUtils.formatTimestamp(LocalContext.current, failedAt),
                                    ),
                                    role = TextRole.BodySmall,
                                    tone = TextTone.Secondary,
                                )
                            }
                            if (errorMsg != null) {
                                CinemaText(errorMsg, role = TextRole.BodySmall, tone = TextTone.Danger)
                            }
                            val failures = currentProvider?.failuresSinceSuccess ?: 0
                            if (storedError != null && failures > 0) {
                                CinemaText(
                                    stringResource(R.string.provider_failures_since_success, failures),
                                    role = TextRole.BodySmall,
                                    tone = TextTone.Secondary,
                                )
                            }
                        }
                        if (syncState is SyncState.Success) {
                            CinemaText(stringResource(R.string.provider_sync_success), role = TextRole.BodySmall, tone = TextTone.Success)
                        }
                        Spacer(modifier = Modifier.height(CinemaSpacing.md))
                    }

                    CinemaText(stringResource(R.string.provider_total_db_items_label))
                    val totalItems =
                        stats.liveTv.itemsCount + stats.movies.itemsCount + stats.tvShows.itemsCount
                    CinemaText(
                        stringResource(R.string.provider_total_items_value, NumberUtils.formatCount(LocalResources.current, totalItems)),
                        role = TextRole.Title,
                        tone = TextTone.Primary,
                    )

                    Spacer(modifier = Modifier.height(CinemaSpacing.md))
                    CinemaDivider()
                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                    // A type the source has nothing of is left out.
                    if (stats.liveTv.itemsCount > 0) {
                        CinemaText(stringResource(R.string.provider_live_tv_label), role = TextRole.Heading)
                        CinemaText(
                            stringResource(
                                R.string.provider_live_tv_stats,
                                NumberUtils.formatCount(LocalResources.current, stats.liveTv.categoryCount),
                                NumberUtils.formatCount(LocalResources.current, stats.liveTv.itemsCount),
                            ),
                            tone = TextTone.Primary,
                        )

                        Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                    }

                    // A type the source has nothing of is left out.
                    if (stats.movies.itemsCount > 0) {
                        CinemaText(stringResource(R.string.provider_movies_label), role = TextRole.Heading)
                        CinemaText(
                            stringResource(
                                R.string.provider_movies_stats,
                                NumberUtils.formatCount(LocalResources.current, stats.movies.categoryCount),
                                NumberUtils.formatCount(LocalResources.current, stats.movies.itemsCount),
                            ),
                            tone = TextTone.Primary,
                        )

                        Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                    }

                    if (stats.tvShows.itemsCount > 0) {
                        CinemaText(stringResource(R.string.provider_tv_shows_label), role = TextRole.Heading)
                        CinemaText(
                            stringResource(
                                R.string.provider_tv_shows_stats,
                                NumberUtils.formatCount(LocalResources.current, stats.tvShows.categoryCount),
                                NumberUtils.formatCount(LocalResources.current, stats.tvShows.itemsCount),
                                NumberUtils.formatCount(LocalResources.current, stats.tvShows.episodesCount),
                            ),
                            tone = TextTone.Primary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Danger zone: every destructive action of this source, one row each — what it removes, then a
 * small error-coloured Clear.
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
    if (!isEditMode) return
    // One row here; the actions sit in their own dialog, out of reach of a stray tap.
    var open by rememberSaveable { mutableStateOf(false) }
    Spacer(modifier = Modifier.height(CinemaSpacing.lg))
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        SettingsListRow(
            title = stringResource(R.string.provider_section_danger_zone),
            summary = stringResource(R.string.provider_danger_zone_summary),
            onClick = { open = true },
        )
    }
    if (open) {
        // An action menu of removals: each row says what it removes and opens its confirm; the
        // per-type clears only for types that have something cached.
        val type: @Composable (Int) -> String = { label -> stringResource(R.string.provider_clear_type_format, stringResource(label)) }
        CinemaActionMenu(
            title = stringResource(R.string.provider_section_danger_zone),
            actions =
                listOfNotNull(
                    MenuAction(
                        CinemaIcons.Delete,
                        stringResource(R.string.provider_clear_favorites_desc),
                        removal = true,
                        onClick = onClearFavorites,
                    ),
                    MenuAction(
                        CinemaIcons.Delete,
                        stringResource(R.string.provider_clear_progress_desc),
                        removal = true,
                        onClick = onClearProgress,
                    ),
                    cacheStats?.let {
                        MenuAction(
                            CinemaIcons.Delete,
                            stringResource(R.string.provider_clear_library_desc),
                            removal = true,
                            onClick = onClearAllCache,
                        )
                    },
                    cacheStats?.takeIf { it.liveTv.itemsCount > 0 }?.let {
                        MenuAction(CinemaIcons.Delete, type(R.string.provider_live_tv_label), removal = true, onClick = onClearLiveTvCache)
                    },
                    cacheStats?.takeIf { it.movies.itemsCount > 0 }?.let {
                        MenuAction(CinemaIcons.Delete, type(R.string.provider_movies_label), removal = true, onClick = onClearMoviesCache)
                    },
                    cacheStats?.takeIf { it.tvShows.itemsCount > 0 }?.let {
                        MenuAction(
                            CinemaIcons.Delete,
                            type(R.string.provider_tv_shows_label),
                            removal = true,
                            onClick = onClearTvShowsCache,
                        )
                    },
                ),
            onDismiss = { open = false },
        )
    }
}
