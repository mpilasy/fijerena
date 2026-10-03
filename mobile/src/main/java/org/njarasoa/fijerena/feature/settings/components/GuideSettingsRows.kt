package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.njarasoa.fijerena.core.network.EPG_REFRESH_INTERVAL_OPTIONS
import org.njarasoa.fijerena.core.network.provider.EpgPipelineStatsEntity
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager.MultiSourceState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDialogActionButton
import org.njarasoa.fijerena.core.ui.components.CinemaDialogTextButton
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModel
import org.njarasoa.fijerena.feature.epg.localizedEpgPhase
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaTextButton
import org.njarasoa.fijerena.ui.theme.Spacing

/*
 * The guide's device-wide controls, moved out of the per-source EPG Management screen into
 * Settings (A-9, M5): auto-refresh under Source & guide, maintenance under Backup & storage.
 * Both still go through [EpgManagementViewModel], which writes the same AppSettings keys.
 */

/** "Guide auto-refresh" value row: frequency and start time, or Disabled; opens its dialog. */
@Composable
fun GuideAutoRefreshRow(viewModel: EpgManagementViewModel) {
    val epgSettings by viewModel.epgSettings.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    val interval = epgSettings.epgRefreshInterval
    val summary =
        if (!epgSettings.autoRefreshEnabled || interval == -1) {
            stringResource(R.string.epg_automation_disabled)
        } else {
            stringResource(R.string.settings_guide_auto_refresh_summary_format, frequencyLabel(interval), epgSettings.epgRefreshTime)
        }
    SettingsListRow(
        title = stringResource(R.string.settings_guide_auto_refresh_title),
        summary = summary,
        scope = SettingsScope.DEVICE,
        onClick = { showDialog = true },
    )
    if (showDialog) {
        GuideAutoRefreshDialog(viewModel = viewModel, onDismiss = { showDialog = false })
    }
}

@Composable
private fun frequencyLabel(interval: Int): String =
    when (interval) {
        -1 -> stringResource(R.string.epg_automation_freq_never)
        24 -> stringResource(R.string.epg_automation_freq_daily)
        else -> stringResource(R.string.epg_automation_freq_hours, interval)
    }

/** The controls EPG Management's Auto-Refresh card had: on/off, frequency, start time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuideAutoRefreshDialog(
    viewModel: EpgManagementViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val epgSettings by viewModel.epgSettings.collectAsStateWithLifecycle()
    val nextRefreshAtMs by viewModel.nextRefreshAtMs.collectAsStateWithLifecycle()
    var showTimePicker by remember { mutableStateOf(false) }
    var showIntervalPicker by remember { mutableStateOf(false) }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_guide_auto_refresh_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.epg_auto_refresh_title), style = MaterialTheme.typography.bodyLarge)
                        val intervalText =
                            when (val interval = epgSettings.epgRefreshInterval) {
                                -1 -> {
                                    stringResource(R.string.epg_automation_disabled)
                                }

                                else -> {
                                    // 0 = no next run (a malformed start time, or not computed yet):
                                    // formatted, it read as the epoch, "Next at 6:00 PM" (R-09).
                                    val timeStr =
                                        android.text.format.DateFormat
                                            .getTimeFormat(context)
                                            .format(java.util.Date(nextRefreshAtMs))
                                    if (nextRefreshAtMs > 0L) {
                                        frequencyLabel(interval) + stringResource(R.string.epg_automation_next_at, timeStr)
                                    } else {
                                        frequencyLabel(interval)
                                    }
                                }
                            }
                        Text(
                            intervalText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                        )
                    }
                    Switch(
                        checked = epgSettings.autoRefreshEnabled,
                        onCheckedChange = { viewModel.setAutoRefreshEnabled(it) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CinemaOutlinedButton(onClick = { showIntervalPicker = true }) {
                        Text(stringResource(R.string.epg_automation_frequency, frequencyLabel(epgSettings.epgRefreshInterval)))
                    }
                    if (epgSettings.epgRefreshInterval != -1) {
                        CinemaOutlinedButton(onClick = { showTimePicker = true }) {
                            Text(stringResource(R.string.epg_automation_start_label, epgSettings.epgRefreshTime))
                        }
                    }
                }
            }
        },
        confirmButton = {
            CinemaDialogTextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        },
    )

    if (showIntervalPicker) {
        SettingsPickerDialog(
            title = stringResource(R.string.epg_refresh_interval_title),
            options =
                EPG_REFRESH_INTERVAL_OPTIONS.map { interval ->
                    val label =
                        if (interval == -1) {
                            stringResource(R.string.epg_automation_freq_never)
                        } else {
                            stringResource(R.string.epg_refresh_interval_hours_format, interval)
                        }
                    label to interval
                },
            selected = epgSettings.epgRefreshInterval,
            onSelect = { interval ->
                viewModel.setEpgRefreshInterval(interval)
                viewModel.setAutoRefreshEnabled(interval != -1)
                showIntervalPicker = false
            },
            onDismiss = { showIntervalPicker = false },
        )
    }

    if (showTimePicker) {
        val parts = epgSettings.epgRefreshTime.split(":")
        val timePickerState =
            rememberTimePickerState(
                initialHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 0,
                initialMinute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0,
                is24Hour = true,
            )
        CinemaAlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.epg_set_refresh_time_title)) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                CinemaDialogActionButton(
                    onClick = {
                        viewModel.setEpgRefreshTime("%02d:%02d".format(timePickerState.hour, timePickerState.minute))
                        showTimePicker = false
                    },
                ) { Text(stringResource(R.string.provider_save_button)) }
            },
            dismissButton = {
                CinemaDialogTextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

/**
 * "Guide data maintenance" row: the guide database's state as its summary; opens the old
 * System Status lines plus Cleanup, Purge and Clear All Data (behind its confirm).
 */
@Composable
fun GuideMaintenanceRow(viewModel: EpgManagementViewModel) {
    val indexState by viewModel.indexState.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    SettingsListRow(
        title = stringResource(R.string.settings_guide_maintenance_title),
        summary = databaseStatusText(indexState),
        scope = SettingsScope.DEVICE,
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
        title = { Text(stringResource(R.string.settings_guide_maintenance_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GuideStatusLines(processingState, indexState, queuedTaskIds, lastPipelineStats)
                Text(
                    stringResource(R.string.epg_maintenance_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    CinemaOutlinedButton(onClick = { viewModel.cleanupFiles() }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.epg_cleanup_btn))
                    }
                    CinemaOutlinedButton(onClick = { viewModel.purgeOldProgrammes() }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.epg_purge_btn))
                    }
                }
                CinemaTextButton(
                    onClick = { showClearConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(CinemaIcons.DeleteForever, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.epg_clear_all_data_btn))
                }
            }
        },
        confirmButton = {
            CinemaDialogTextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
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
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                ) { Text(stringResource(R.string.epg_clear_everything_btn)) }
            },
            dismissButton = {
                CinemaDialogTextButton(onClick = { showClearConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            },
            title = { Text(stringResource(R.string.epg_clear_db_confirm_title)) },
            text = { Text(stringResource(R.string.epg_clear_db_confirm_message)) },
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
