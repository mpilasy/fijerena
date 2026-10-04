@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.settings.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.provider.EpgPipelineStatsEntity
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager.MultiSourceState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModel
import org.njarasoa.fijerena.feature.epg.localizedEpgPhase
import org.njarasoa.fijerena.feature.provider.components.ProviderDangerButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

/*
 * The guide's device-wide maintenance, moved out of the per-source EPG Management screen into
 * Settings → Backup & storage (A-9, T6, as M5 did on mobile): a value row that drills into a
 * sub-pane in place of the group's rows, like the choice pickers. Auto-refresh is set on each
 * guide source's row (docs/plans/20261003_sources-guide-profiles-plan.md → P5b).
 */

/** "Guide data maintenance" card (Backup & storage): the guide database's state; OK opens [GuideMaintenancePane]. */
@Composable
fun GuideMaintenanceCard(
    viewModel: EpgManagementViewModel,
    onOpen: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val scale = LocalUiScale.current
    val indexState by viewModel.indexState.collectAsStateWithLifecycle()
    GlassPanel(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs.scaled(scale))) {
        SettingsRow(
            title = stringResource(R.string.settings_guide_maintenance_title),
            description = stringResource(R.string.epg_maintenance_desc_tv),
            value = databaseStatusText(indexState),
            scope = SettingsScope.DEVICE,
            onClick = onOpen,
            modifier = Modifier.padding(Spacing.md.scaled(scale)),
            focusRequester = focusRequester,
        )
    }
}

/**
 * EPG Management's former System Status lines, then Cleanup and Purge, then Clear All Data last,
 * in error style and behind its confirm. Focus opens on Cleanup, never on the destructive action.
 */
@Composable
fun GuideMaintenancePane(
    viewModel: EpgManagementViewModel,
    onBack: () -> Unit,
) {
    val scale = LocalUiScale.current
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()
    val indexState by viewModel.indexState.collectAsStateWithLifecycle()
    val queuedTaskIds by viewModel.queuedTaskIds.collectAsStateWithLifecycle()
    val lastPipelineStats by viewModel.lastPipelineStats.collectAsStateWithLifecycle()
    var showClearConfirm by remember { mutableStateOf(false) }
    val cleanupFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { cleanupFocus.requestFocusWithRetry() }

    SettingsSubPane(title = stringResource(R.string.settings_guide_maintenance_title), onBack = onBack) {
        GuideStatusLines(processingState, indexState, queuedTaskIds, lastPipelineStats)
        Text(
            stringResource(R.string.epg_maintenance_desc_tv),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
        )
        CinemaSecondaryButton(
            onClick = { viewModel.cleanupFiles() },
            text = stringResource(R.string.epg_cleanup_btn),
            modifier = Modifier.focusRequester(cleanupFocus),
        )
        CinemaSecondaryButton(
            onClick = { viewModel.purgeOldProgrammes() },
            text = stringResource(R.string.epg_purge_btn),
        )
        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
        Text(
            text = stringResource(R.string.provider_section_danger_zone),
            style = MaterialTheme.typography.titleSmall,
            color = CinemaError,
        )
        ProviderDangerButton(
            onClick = { showClearConfirm = true },
            text = stringResource(R.string.epg_clear_all_data_btn),
        )
    }

    if (showClearConfirm) {
        CinemaAlertDialog(
            onDismissRequest = { showClearConfirm = false },
            confirmButton = {
                CinemaDangerButton(
                    onClick = {
                        viewModel.clearDatabase()
                        showClearConfirm = false
                    },
                    text = stringResource(R.string.epg_clear_everything_btn),
                )
            },
            dismissButton = {
                CinemaSecondaryButton(
                    onClick = { showClearConfirm = false },
                    text = stringResource(R.string.common_cancel),
                )
            },
            title = {
                Text(
                    stringResource(R.string.epg_clear_db_confirm_title),
                    color = CinemaTextPrimary,
                )
            },
            text = {
                Text(
                    stringResource(R.string.epg_clear_db_confirm_message),
                    color = CinemaTextSecondary,
                )
            },
            containerColor = CinemaSurface,
        )
    }
}

/**
 * A drill-in sub-pane: `‹ Title` and a single column of controls in place of the group's rows,
 * like [SettingsPickerPane]. Back and Left leave it (Left = back one level; every control in it
 * sits in one column, so Left has nothing else to do). The caller lands focus on its first control.
 */
@Composable
private fun SettingsSubPane(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(
        modifier =
            Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.Back -> {
                        if (event.type == KeyEventType.KeyUp) onBack()
                        true
                    }

                    Key.DirectionLeft -> {
                        if (event.type == KeyEventType.KeyDown) onBack()
                        true
                    }

                    else -> {
                        false
                    }
                }
            },
    ) {
        Text(
            text = "‹ $title",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = Spacing.xs),
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            content = content,
        )
    }
}

@Composable
private fun databaseStatusText(indexState: EpgIndexState): String =
    when (indexState) {
        is EpgIndexState.Indexed -> {
            stringResource(
                R.string.epg_database_label,
                stringResource(R.string.epg_database_ready, NumberUtils.formatCount(indexState.programmeCount)),
            )
        }

        is EpgIndexState.Indexing -> {
            stringResource(R.string.epg_database_label, stringResource(R.string.epg_database_indexing))
        }

        is EpgIndexState.Optimizing -> {
            stringResource(R.string.epg_database_label, stringResource(R.string.epg_database_optimizing))
        }

        is EpgIndexState.NotIndexed -> {
            stringResource(R.string.epg_database_label, stringResource(R.string.epg_database_empty))
        }

        is EpgIndexState.Failed -> {
            stringResource(R.string.epg_database_error_prefixed, indexState.reason)
        }
    }

/** EPG Management's former System Status card: database, current run, last run. */
@Composable
private fun GuideStatusLines(
    multiState: MultiSourceState,
    indexState: EpgIndexState,
    queuedTaskIds: Set<String>,
    lastRun: EpgPipelineStatsEntity?,
) {
    Column {
        Text(databaseStatusText(indexState), style = MaterialTheme.typography.bodySmall)

        val currentStatusText =
            when (multiState) {
                is MultiSourceState.Idle -> {
                    val queued = queuedTaskIds.count { it.startsWith("epg_refresh_") }
                    if (queued > 0) {
                        stringResource(R.string.epg_current_status, stringResource(R.string.epg_status_tasks_queued, queued))
                    } else {
                        stringResource(R.string.epg_current_status, stringResource(R.string.epg_status_idle))
                    }
                }

                is MultiSourceState.Processing -> {
                    stringResource(
                        R.string.epg_current_status,
                        stringResource(R.string.epg_status_processing, multiState.completedCount, multiState.totalSources),
                    )
                }

                is MultiSourceState.Retrying -> {
                    val nextRetry = NumberUtils.formatTimestamp(LocalContext.current, multiState.nextRetryAtMs)
                    stringResource(
                        R.string.epg_current_status,
                        stringResource(R.string.epg_status_retrying, multiState.attempt, multiState.maxAttempts, nextRetry),
                    )
                }

                is MultiSourceState.Completed -> {
                    stringResource(R.string.epg_current_status, stringResource(R.string.epg_status_finished))
                }

                is MultiSourceState.Finalizing -> {
                    stringResource(
                        R.string.epg_current_status,
                        stringResource(R.string.epg_status_finalizing, localizedEpgPhase(multiState.phase)),
                    )
                }

                is MultiSourceState.Clearing -> {
                    stringResource(R.string.epg_current_status, stringResource(R.string.epg_status_clearing))
                }

                is MultiSourceState.Error -> {
                    stringResource(R.string.epg_current_status_error, multiState.reason)
                }

                else -> {
                    stringResource(R.string.epg_current_status, stringResource(R.string.epg_status_idle))
                }
            }
        Text(currentStatusText, style = MaterialTheme.typography.bodySmall)

        lastRun?.let { stats ->
            val time = NumberUtils.formatTimestamp(LocalContext.current, stats.updatedAtMs)
            val duration = NumberUtils.formatDuration(stats.durationMs)
            val errorText = if (stats.errors > 0) stringResource(R.string.epg_last_run_errors, stats.errors) else ""
            Text(
                text = stringResource(R.string.epg_last_run_stats, time, stats.sourcesProcessed, duration, errorText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
            )
        }
    }
}
