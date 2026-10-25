package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.njarasoa.fijerena.core.network.provider.EpgPipelineStatsEntity
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager.MultiSourceState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModel
import org.njarasoa.fijerena.feature.epg.localizedEpgPhase
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaTextButton
import org.njarasoa.fijerena.ui.theme.Spacing

/*
 * The guide's device-wide maintenance, in Settings → Sources & guide (A-9, M5). Auto-refresh is
 * set on each guide source's row
 * (the sources guide profiles plan → P5b).
 */

/**
 * "Guide data maintenance" row: the guide database's state as its summary; opens the System
 * Status lines plus Cleanup, Purge and Clear All Data (behind its confirm).
 */
@Composable
fun GuideMaintenanceRow(viewModel: EpgManagementViewModel) {
    val indexState by viewModel.indexState.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    SettingsListRow(
        title = stringResource(R.string.settings_guide_maintenance_title),
        summary = databaseStatusText(indexState),
        onClick = { showDialog = true },
    )
    if (showDialog) {
        GuideMaintenanceDialog(viewModel = viewModel, onDismiss = { showDialog = false })
    }
}

@Composable
private fun GuideMaintenanceDialog(
    viewModel: EpgManagementViewModel,
    onDismiss: () -> Unit,
) {
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()
    val indexState by viewModel.indexState.collectAsStateWithLifecycle()
    val queuedTaskIds by viewModel.queuedTaskIds.collectAsStateWithLifecycle()
    val lastPipelineStats by viewModel.lastPipelineStats.collectAsStateWithLifecycle()
    var showClearConfirm by remember { mutableStateOf(false) }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { CinemaText(stringResource(R.string.settings_guide_maintenance_title), role = TextRole.Title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GuideStatusLines(processingState, indexState, queuedTaskIds, lastPipelineStats)
                CinemaText(stringResource(R.string.epg_maintenance_desc), role = TextRole.BodySmall, tone = TextTone.Secondary)
                // One under the other: each label says what it deletes.
                CinemaOutlinedButton(onClick = { viewModel.cleanupFiles() }, modifier = Modifier.fillMaxWidth()) {
                    CinemaText(stringResource(R.string.epg_cleanup_btn), role = TextRole.Label, tone = TextTone.Inherit)
                }
                CinemaOutlinedButton(onClick = { viewModel.purgeOldProgrammes() }, modifier = Modifier.fillMaxWidth()) {
                    CinemaText(stringResource(R.string.epg_purge_btn), role = TextRole.Label, tone = TextTone.Inherit)
                }
                CinemaButton(
                    onClick = { showClearConfirm = true },
                    kind = ButtonKind.Danger,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(CinemaIcons.DeleteForever, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    CinemaText(stringResource(R.string.epg_clear_all_data_btn), role = TextRole.Label, tone = TextTone.Inherit)
                }
            }
        },
        confirmButton = {
            CinemaDialogActionButton(onClick = onDismiss, kind = ButtonKind.Other) {
                CinemaText(stringResource(R.string.common_close), role = TextRole.Label, tone = TextTone.Inherit)
            }
        },
    )

    if (showClearConfirm) {
        CinemaAlertDialog(
            onDismissRequest = { showClearConfirm = false },
            confirmButton = {
                CinemaDialogActionButton(
                    onClick = {
                        viewModel.clearDatabase()
                        showClearConfirm = false
                    },
                    kind = ButtonKind.Danger,
                ) { CinemaText(stringResource(R.string.epg_clear_everything_btn), role = TextRole.Label, tone = TextTone.Inherit) }
            },
            dismissButton = {
                CinemaDialogActionButton(
                    onClick = { showClearConfirm = false },
                    kind = ButtonKind.Other,
                ) { CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit) }
            },
            title = { CinemaText(stringResource(R.string.epg_clear_db_confirm_title), role = TextRole.Title) },
            text = { CinemaText(stringResource(R.string.epg_clear_db_confirm_message), tone = TextTone.Secondary) },
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

/** The guide's System Status lines: database, current run, last run. */
@Composable
private fun GuideStatusLines(
    multiState: MultiSourceState,
    indexState: EpgIndexState,
    queuedTaskIds: Set<String>,
    lastRun: EpgPipelineStatsEntity?,
) {
    Column {
        CinemaText(databaseStatusText(indexState), role = TextRole.BodySmall)

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
        CinemaText(currentStatusText, role = TextRole.BodySmall)

        lastRun?.let { stats ->
            val time = NumberUtils.formatTimestamp(LocalContext.current, stats.updatedAtMs)
            val duration = NumberUtils.formatDuration(stats.durationMs)
            val errorText = if (stats.errors > 0) stringResource(R.string.epg_last_run_errors, stats.errors) else ""
            CinemaText(
                stringResource(R.string.epg_last_run_stats, time, stats.sourcesProcessed, duration, errorText),
                role = TextRole.BodySmall,
                tone = TextTone.Secondary,
            )
        }
    }
}
