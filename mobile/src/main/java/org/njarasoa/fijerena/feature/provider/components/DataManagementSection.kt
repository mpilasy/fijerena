package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.player.diagnostics.Redact
import org.njarasoa.fijerena.core.player.domain.ProviderType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaDivider
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SyncState
import org.njarasoa.fijerena.feature.settings.components.SettingsListRow
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
                            val errorMsg = ((syncState as? SyncState.Error)?.message ?: currentProvider?.lastSyncError)?.let(Redact::text)
                            if (errorMsg != null) {
                                CinemaText(errorMsg, role = TextRole.BodySmall, tone = TextTone.Danger)
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
                        stringResource(R.string.provider_total_items_value, NumberUtils.formatCount(totalItems)),
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
                                NumberUtils.formatCount(stats.liveTv.categoryCount),
                                NumberUtils.formatCount(stats.liveTv.itemsCount),
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
                                NumberUtils.formatCount(stats.movies.categoryCount),
                                NumberUtils.formatCount(stats.movies.itemsCount),
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
                                NumberUtils.formatCount(stats.tvShows.categoryCount),
                                NumberUtils.formatCount(stats.tvShows.itemsCount),
                                NumberUtils.formatCount(stats.tvShows.episodesCount),
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
    // One row here; the actions sit on their own page, out of reach of a stray tap.
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
        Dialog(
            onDismissRequest = { open = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(CinemaTheme.colors.background)
                        .verticalScroll(rememberScrollState())
                        .padding(CinemaSpacing.md),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { open = false }) {
                        Icon(CinemaIcons.ArrowBack, stringResource(R.string.player_back))
                    }
                    ProviderSectionTitle(
                        title = stringResource(R.string.provider_section_danger_zone),
                        titleTone = TextTone.Danger,
                    )
                }
                Spacer(modifier = Modifier.height(CinemaSpacing.md))
                // One row per action: what it removes, and a small Clear (each opens its confirm).
                DangerRow(label = stringResource(R.string.provider_clear_favorites_desc), onClear = onClearFavorites)
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                DangerRow(label = stringResource(R.string.provider_clear_progress_desc), onClear = onClearProgress)

                cacheStats?.let { stats ->
                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                    DangerRow(label = stringResource(R.string.provider_clear_library_desc), onClear = onClearAllCache)

                    // Per-type clears: only for types that have something to clear
                    if (stats.liveTv.itemsCount > 0) {
                        Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                        DangerRow(
                            label = stringResource(R.string.provider_clear_type_format, stringResource(R.string.provider_live_tv_label)),
                            onClear = onClearLiveTvCache,
                        )
                    }
                    if (stats.movies.itemsCount > 0) {
                        Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                        DangerRow(
                            label = stringResource(R.string.provider_clear_type_format, stringResource(R.string.provider_movies_label)),
                            onClear = onClearMoviesCache,
                        )
                    }
                    if (stats.tvShows.itemsCount > 0) {
                        Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                        DangerRow(
                            label = stringResource(R.string.provider_clear_type_format, stringResource(R.string.provider_tv_shows_label)),
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
        CinemaText(label, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(CinemaSpacing.sm))
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
