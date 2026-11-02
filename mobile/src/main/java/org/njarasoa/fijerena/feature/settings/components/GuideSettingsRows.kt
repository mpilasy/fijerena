package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.njarasoa.fijerena.core.network.provider.EpgPipelineStatsEntity
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager.MultiSourceState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.MenuAction
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModel
import org.njarasoa.fijerena.feature.epg.localizedEpgPhase
import org.njarasoa.fijerena.ui.components.CinemaActionMenu
import org.njarasoa.fijerena.ui.components.CinemaAlertDialog

/*
 * The guide's device-wide maintenance, in Settings → Sources & guide (A-9, M5). Auto-refresh is
 * set on each guide source's row
 * (the sources guide profiles plan → P5b).
 */

/**
 * "Guide data maintenance" row: the guide's System Status lines (database, current run, last run)
 * as its summary; it opens the maintenance actions as an action menu: Cleanup, Purge, then Clear
 * All Data last, as a removal, behind its confirm (phone walk A, item 3; TV's maintenance page has
 * the same actions in the same order).
 */
@Composable
fun GuideMaintenanceRow(viewModel: EpgManagementViewModel) {
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()
    val indexState by viewModel.indexState.collectAsStateWithLifecycle()
    val queuedTaskIds by viewModel.queuedTaskIds.collectAsStateWithLifecycle()
    val lastPipelineStats by viewModel.lastPipelineStats.collectAsStateWithLifecycle()
    var showMenu by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    SettingsListRow(
        title = stringResource(R.string.settings_guide_maintenance_title),
        summary = guideStatusLines(processingState, indexState, queuedTaskIds, lastPipelineStats).joinToString("\n"),
        onClick = { showMenu = true },
    )
    if (showMenu) {
        CinemaActionMenu(
            title = stringResource(R.string.settings_guide_maintenance_title),
            actions =
                listOf(
                    MenuAction(CinemaIcons.Folder, stringResource(R.string.epg_cleanup_btn)) { viewModel.cleanupFiles() },
                    MenuAction(CinemaIcons.DateRange, stringResource(R.string.epg_purge_btn)) { viewModel.purgeOldProgrammes() },
                    MenuAction(CinemaIcons.DeleteForever, stringResource(R.string.epg_clear_all_data_btn), removal = true) {
                        showClearConfirm = true
                    },
                ),
            onDismiss = { showMenu = false },
        )
    }
    if (showClearConfirm) {
        CinemaAlertDialog(
            onDismissRequest = { showClearConfirm = false },
            danger =
                DialogAction(stringResource(R.string.epg_clear_everything_btn)) {
                    viewModel.clearDatabase()
                    showClearConfirm = false
                },
            cancel = DialogAction(stringResource(R.string.common_cancel)) { showClearConfirm = false },
            title = stringResource(R.string.epg_clear_db_confirm_title),
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
private fun guideStatusLines(
    multiState: MultiSourceState,
    indexState: EpgIndexState,
    queuedTaskIds: Set<String>,
    lastRun: EpgPipelineStatsEntity?,
): List<String> {
    val lines = mutableListOf(databaseStatusText(indexState))
    run {
        val currentStatusText =
            when (multiState) {
                is MultiSourceState.Idle -> {
                    val queued = queuedTaskIds.count { it.startsWith("epg_refresh_") }
                    if (queued > 0) {
                        stringResource(R.string.epg_current_status, pluralStringResource(R.plurals.epg_status_tasks_queued, queued, queued))
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
        lines += currentStatusText

        lastRun?.let { stats ->
            val time = NumberUtils.formatTimestamp(LocalContext.current, stats.updatedAtMs)
            val duration = NumberUtils.formatDuration(stats.durationMs)
            // The space is added here: a leading space in a string resource is dropped.
            val errorText =
                if (stats.errors >
                    0
                ) {
                    " " + pluralStringResource(R.plurals.epg_last_run_errors, stats.errors, stats.errors)
                } else {
                    ""
                }
            lines += stringResource(R.string.epg_last_run_stats, time, stats.sourcesProcessed, duration, errorText)
        }
    }
    return lines
}
