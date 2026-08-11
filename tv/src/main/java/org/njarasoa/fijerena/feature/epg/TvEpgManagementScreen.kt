package org.njarasoa.fijerena.feature.epg

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import org.njarasoa.fijerena.core.network.EPG_REFRESH_INTERVAL_OPTIONS
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager.MultiSourceState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.core.ui.R
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun TvEpgManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: EpgManagementViewModel =
        viewModel(
            factory = remember { SettingsViewModelFactory(context.applicationContext) },
        )

    val sources by viewModel.sources.collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val latestProgrammeTimes by viewModel.latestProgrammeTimes.collectAsStateWithLifecycle()
    val staleSourceCount by viewModel.staleSourceCount.collectAsStateWithLifecycle()
    val failedSourceCount by viewModel.failedSourceCount.collectAsStateWithLifecycle()
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()
    val indexState by viewModel.indexState.collectAsStateWithLifecycle()
    val queuedTaskIds by viewModel.queuedTaskIds.collectAsStateWithLifecycle()
    val lastPipelineStats by viewModel.lastPipelineStats.collectAsStateWithLifecycle()
    val epgSettings by viewModel.epgSettings.collectAsStateWithLifecycle()
    val nextRefreshAtMs by viewModel.nextRefreshAtMs.collectAsStateWithLifecycle()

    val nowMs = remember { System.currentTimeMillis() }

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { message ->
            android.widget.Toast
                .makeText(context, message.asString(context), android.widget.Toast.LENGTH_SHORT)
                .show()
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<EpgSourceEntity?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showIntervalPicker by remember { mutableStateOf(false) }
    val intervalOptions = EPG_REFRESH_INTERVAL_OPTIONS
    var deletingSource by remember { mutableStateOf<EpgSourceEntity?>(null) }
    var deleteSelectedIds by remember { mutableStateOf<Set<Long>?>(null) }

    val scale = LocalUiScale.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = Spacing.tvSafeMarginHorizontal,
                        vertical = Spacing.tvSafeMarginVertical,
                    ),
        ) {
            Text(
                text = stringResource(R.string.epg_management_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))

            TvLazyColumn(
                contentPadding = PaddingValues(vertical = Spacing.xs.scaled(scale)),
                verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                modifier = Modifier.fillMaxSize(),
            ) {
                // Header Actions
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                    ) {
                        CinemaPrimaryButton(
                            onClick = { showAddDialog = true },
                            text = stringResource(R.string.epg_add_source),
                        )

                        if (selectedIds.isNotEmpty()) {
                            CinemaSecondaryButton(
                                onClick = {
                                    viewModel.refreshSelected(selectedIds)
                                    viewModel.clearSelection()
                                },
                                text = stringResource(R.string.epg_refresh_selected_btn, selectedIds.size),
                            )

                            CinemaDangerButton(
                                onClick = { deleteSelectedIds = selectedIds },
                                text = stringResource(R.string.epg_delete_selected_btn, selectedIds.size),
                            )
                        }

                        if (staleSourceCount > 0) {
                            CinemaSecondaryButton(
                                onClick = { viewModel.refreshStale() },
                                text = stringResource(R.string.epg_refresh_stale_btn, staleSourceCount),
                            )
                        }

                        if (failedSourceCount > 0) {
                            CinemaSecondaryButton(
                                onClick = { viewModel.refreshFailed() },
                                text = stringResource(R.string.epg_retry_failed_btn, failedSourceCount),
                            )
                        }
                    }
                }

                // Processing Status
                item {
                    EpgStatusCard(processingState, indexState, queuedTaskIds, lastPipelineStats, scale)
                }

                // Maintenance Actions
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                    ) {
                        // Maintenance Card
                        GlassPanel(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.padding(Spacing.md.scaled(scale)),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.epg_maintenance_title), style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        stringResource(R.string.epg_maintenance_desc_tv),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                                    )
                                }

                                CinemaSecondaryButton(
                                    onClick = { viewModel.cleanupFiles() },
                                    text = stringResource(R.string.epg_cleanup_btn),
                                )

                                CinemaSecondaryButton(
                                    onClick = { viewModel.purgeOldProgrammes() },
                                    text = stringResource(R.string.epg_purge_btn),
                                )

                                CinemaDangerButton(
                                    onClick = { showClearConfirm = true },
                                    text = stringResource(R.string.epg_clear_all_data_btn),
                                )
                            }
                        }

                        // Automation Card
                        GlassPanel(modifier = Modifier.weight(0.6f)) {
                            Column(
                                modifier = Modifier.padding(Spacing.md.scaled(scale)),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text(stringResource(R.string.epg_auto_refresh_title), style = MaterialTheme.typography.titleMedium)
                                        val intervalText = when (val interval = epgSettings.epgRefreshInterval) {
                                            -1 -> stringResource(R.string.epg_automation_disabled)
                                            else -> {
                                                val freq = if (interval == 24) stringResource(R.string.epg_automation_freq_daily) else stringResource(R.string.epg_automation_freq_hours, interval)
                                                val timeStr = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(nextRefreshAtMs))
                                                freq + stringResource(R.string.epg_automation_next_at, timeStr)
                                            }
                                        }
                                        Text(
                                            intervalText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                                        )
                                    }

                                    androidx.tv.material3.Surface(
                                        checked = epgSettings.autoRefreshEnabled,
                                        onCheckedChange = { viewModel.setAutoRefreshEnabled(it) },
                                        colors =
                                            androidx.tv.material3.ToggleableSurfaceDefaults.colors(
                                                containerColor = org.njarasoa.fijerena.core.ui.theme.CinemaSurface.copy(alpha = org.njarasoa.fijerena.core.ui.theme.CinemaAlpha.glass),
                                                contentColor = org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary,
                                                focusedContainerColor = org.njarasoa.fijerena.core.ui.theme.CinemaAccent,
                                                focusedContentColor = org.njarasoa.fijerena.core.ui.theme.CinemaBackground,
                                            ),
                                        scale =
                                            androidx.tv.material3.ToggleableSurfaceDefaults.scale(
                                                focusedScale = 1.15f,
                                            ),
                                        shape = androidx.tv.material3.ToggleableSurfaceDefaults.shape(shape = androidx.compose.foundation.shape.CircleShape),
                                    ) {
                                        androidx.compose.material3.Switch(
                                            checked = viewModel.autoRefreshEnabled,
                                            onCheckedChange = null,
                                            modifier = Modifier.padding(Spacing.xxs.scaled(scale)),
                                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                                checkedThumbColor = org.njarasoa.fijerena.core.ui.theme.CinemaBackground,
                                                checkedTrackColor = org.njarasoa.fijerena.core.ui.theme.CinemaAccent,
                                                uncheckedThumbColor = org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary,
                                                uncheckedTrackColor = org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
                                            )
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
                                ) {
                                    CinemaSecondaryButton(
                                        onClick = { showIntervalPicker = true },
                                        text = when (val interval = epgSettings.epgRefreshInterval) {
                                            -1 -> stringResource(R.string.epg_automation_frequency, stringResource(R.string.epg_automation_freq_never))
                                            24 -> stringResource(R.string.epg_automation_frequency, stringResource(R.string.epg_automation_freq_daily))
                                            else -> stringResource(R.string.epg_automation_frequency, stringResource(R.string.epg_automation_freq_hours, interval))
                                        },
                                    )
                                    if (epgSettings.epgRefreshInterval != -1) {
                                        CinemaSecondaryButton(
                                            onClick = { showTimePicker = true },
                                            text = stringResource(R.string.epg_automation_start_label, epgSettings.epgRefreshTime),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Source rows
                items(sources, key = { it.id }, contentType = { "source" }) { source ->
                    val isSelected = selectedIds.contains(source.id)
                    val latestTime = latestProgrammeTimes[source.id] ?: 0L

                    // Look for active progress for this source
                    val activeProgress =
                        if (processingState is MultiSourceState.Processing) {
                            (processingState as MultiSourceState.Processing).activeProgress[source.id]
                        } else {
                            null
                        }

                    GlassPanel(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                            ) {
                                androidx.tv.material3.Surface(
                                    checked = isSelected,
                                    onCheckedChange = { viewModel.toggleSelection(source.id) },
                                    colors =
                                        androidx.tv.material3.ToggleableSurfaceDefaults.colors(
                                            containerColor = org.njarasoa.fijerena.core.ui.theme.CinemaSurface.copy(alpha = org.njarasoa.fijerena.core.ui.theme.CinemaAlpha.glass),
                                            contentColor = org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary,
                                            focusedContainerColor = org.njarasoa.fijerena.core.ui.theme.CinemaAccent,
                                            focusedContentColor = org.njarasoa.fijerena.core.ui.theme.CinemaBackground,
                                            selectedContainerColor = org.njarasoa.fijerena.core.ui.theme.CinemaAccent.copy(alpha = 0.2f),
                                            selectedContentColor = org.njarasoa.fijerena.core.ui.theme.CinemaAccent,
                                        ),
                                    scale =
                                        androidx.tv.material3.ToggleableSurfaceDefaults.scale(
                                            focusedScale = 1.15f,
                                        ),
                                    shape = androidx.tv.material3.ToggleableSurfaceDefaults.shape(shape = androidx.compose.foundation.shape.CircleShape),
                                ) {
                                    Box(
                                        modifier = Modifier.size(40.dp.scaled(scale)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) CinemaIcons.CheckCircle else CinemaIcons.RadioButtonUnchecked,
                                            contentDescription = if (isSelected) stringResource(R.string.epg_source_selected_description) else stringResource(R.string.epg_source_not_selected_description),
                                            modifier = Modifier.size(24.dp.scaled(scale)),
                                        )
                                    }
                                }

                                StatusIndicator(source, nowMs, viewModel.staleThresholdMs, scale)

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = source.label.ifBlank { stringResource(R.string.epg_unnamed_source) },
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = source.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
                                CinemaSecondaryButton(
                                    onClick = { viewModel.refreshSource(source.id) },
                                    text = stringResource(R.string.common_refresh),
                                )
                                CinemaSecondaryButton(
                                    onClick = { editingSource = source },
                                    text = stringResource(R.string.provider_edit_button),
                                )
                                CinemaDangerButton(
                                    onClick = { deletingSource = source },
                                    text = stringResource(R.string.provider_delete_button),
                                )
                            }

                            if (activeProgress != null) {
                                Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp.scaled(scale))) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.epg_source_phase_format, localizedEpgPhase(activeProgress.phase)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = stringResource(R.string.epg_source_progress_percent, activeProgress.progressPercent),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { activeProgress.progressPercent / 100f },
                                        modifier = Modifier.fillMaxWidth().height(4.dp.scaled(scale)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

                                // Source Stats Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg.scaled(scale)),
                                ) {
                                    val lastIngested =
                                        if (source.lastIngestedAtMs > 0) {
                                            java.text
                                                .SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                                                .format(java.util.Date(source.lastIngestedAtMs))
                                        } else {
                                            stringResource(R.string.epg_source_never)
                                        }

                                    val latestProgStr =
                                        if (latestTime > 0) {
                                            java.text
                                                .SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                                                .format(java.util.Date(latestTime * 1000L))
                                        } else {
                                            stringResource(R.string.epg_source_none)
                                        }

                                    SourceStat(stringResource(R.string.epg_source_stat_label), lastIngested, scale)
                                    SourceStat(stringResource(R.string.epg_source_stat_download), NumberUtils.formatDuration(source.lastDownloadDurationMs), scale)
                                    SourceStat(stringResource(R.string.epg_source_stat_ingest), NumberUtils.formatDuration(source.lastIngestionDurationMs), scale)
                                    SourceStat(stringResource(R.string.epg_source_stat_latest), latestProgStr, scale)
                                    SourceStat(stringResource(R.string.epg_source_stat_channels), NumberUtils.formatCount(source.lastChannels), scale)
                                    SourceStat(stringResource(R.string.epg_source_stat_programmes), NumberUtils.formatCount(source.lastProgrammes), scale)

                                    val lastError = source.lastError
                                    if (lastError != null) {
                                        Text(
                                            text = stringResource(R.string.epg_database_error, lastError),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        EpgSourceEditDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { url, label, tz, method, enabled ->
                viewModel.addSource(url, label, tz, method, enabled)
                showAddDialog = false
            },
            scale = scale,
        )
    }

    editingSource?.let { source ->
        EpgSourceEditDialog(
            initialSource = source,
            onDismiss = { editingSource = null },
            onConfirm = { url, label, tz, method, enabled ->
                viewModel.updateSource(
                    source.copy(url = url, label = label, timezoneOffsetHours = tz, ingestMethod = method, enabled = enabled),
                )
                editingSource = null
            },
            scale = scale,
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
            title = { Text(stringResource(R.string.epg_clear_db_confirm_title), color = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary) },
            text = { Text(stringResource(R.string.epg_clear_db_confirm_message), color = org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary) },
            containerColor = org.njarasoa.fijerena.core.ui.theme.CinemaSurface,
        )
    }

    deletingSource?.let { source ->
        CinemaAlertDialog(
            onDismissRequest = { deletingSource = null },
            confirmButton = {
                CinemaDangerButton(
                    onClick = {
                        viewModel.deleteSource(source.id)
                        deletingSource = null
                    },
                    text = stringResource(R.string.provider_delete_button),
                )
            },
            dismissButton = {
                CinemaSecondaryButton(
                    onClick = { deletingSource = null },
                    text = stringResource(R.string.common_cancel),
                )
            },
            title = { Text(stringResource(R.string.epg_delete_source_confirm_title), color = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary) },
            text = { Text(stringResource(R.string.epg_delete_source_message_tv, source.label.ifBlank { source.url }), color = org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary) },
            containerColor = org.njarasoa.fijerena.core.ui.theme.CinemaSurface,
        )
    }

    deleteSelectedIds?.let { idsToDelete ->
        CinemaAlertDialog(
            onDismissRequest = { deleteSelectedIds = null },
            confirmButton = {
                CinemaDangerButton(
                    onClick = {
                        viewModel.deleteSelected(idsToDelete)
                        deleteSelectedIds = null
                    },
                    text = stringResource(R.string.epg_delete_sources_count_btn, idsToDelete.size),
                )
            },
            dismissButton = {
                CinemaSecondaryButton(
                    onClick = { deleteSelectedIds = null },
                    text = stringResource(R.string.common_cancel),
                )
            },
            title = { Text(stringResource(R.string.epg_delete_selected_confirm_title), color = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary) },
            text = { Text(stringResource(R.string.epg_delete_selected_confirm_message, idsToDelete.size), color = org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary) },
            containerColor = org.njarasoa.fijerena.core.ui.theme.CinemaSurface,
        )
    }

    if (showTimePicker) {
        TvTimePickerDialog(
            initialTime = epgSettings.epgRefreshTime,
            onDismiss = { showTimePicker = false },
            onConfirm = { time ->
                viewModel.setEpgRefreshTime(time)
                showTimePicker = false
            },
            scale = scale,
        )
    }

    if (showIntervalPicker) {
        CinemaAlertDialog(
            onDismissRequest = { showIntervalPicker = false },
            title = { Text(stringResource(R.string.epg_refresh_interval_title), color = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale))) {
                    intervalOptions.forEach { interval ->
                        val label = if (interval == -1) stringResource(R.string.epg_automation_freq_never) else stringResource(R.string.epg_refresh_interval_hours_format, interval)
                        val isSelected = epgSettings.epgRefreshInterval == interval
                        androidx.tv.material3.Surface(
                            checked = isSelected,
                            onCheckedChange = { 
                                viewModel.setEpgRefreshInterval(interval)
                                if (interval == -1) {
                                    viewModel.setAutoRefreshEnabled(false)
                                } else {
                                    viewModel.setAutoRefreshEnabled(true)
                                }
                                showIntervalPicker = false
                            },
                            colors = androidx.tv.material3.ToggleableSurfaceDefaults.colors(
                                containerColor = if (isSelected) org.njarasoa.fijerena.core.ui.theme.CinemaAccent.copy(alpha = 0.2f) else org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant.copy(alpha = 0.5f),
                                contentColor = if (isSelected) org.njarasoa.fijerena.core.ui.theme.CinemaAccent else org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary,
                                focusedContainerColor = org.njarasoa.fijerena.core.ui.theme.CinemaAccent,
                                focusedContentColor = org.njarasoa.fijerena.core.ui.theme.CinemaBackground
                            ),
                            shape = androidx.tv.material3.ToggleableSurfaceDefaults.shape(MaterialTheme.shapes.small),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(Spacing.sm.scaled(scale)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isSelected) CinemaIcons.RadioButtonChecked else CinemaIcons.RadioButtonUnchecked,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp.scaled(scale)),
                                    tint = if (isSelected) org.njarasoa.fijerena.core.ui.theme.CinemaAccent else org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))
                                Text(label)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            containerColor = org.njarasoa.fijerena.core.ui.theme.CinemaSurface,
        )
    }
}

@Composable
private fun localizedEpgPhase(phase: String): String =
    when (phase) {
        "Downloading" -> stringResource(R.string.epg_phase_downloading)
        "Ingesting" -> stringResource(R.string.epg_phase_ingesting)
        "Awaiting Ingestion" -> stringResource(R.string.epg_phase_awaiting_ingestion)
        "Rebuilding indexes…" -> stringResource(R.string.epg_phase_rebuilding_indexes)
        "Swapping to primary guide…" -> stringResource(R.string.epg_phase_swapping_primary_guide)
        else -> phase
    }

@Composable
private fun StatusIndicator(
    source: EpgSourceEntity,
    nowMs: Long,
    staleThresholdMs: Long,
    scale: Float,
) {
    val color =
        when {
            !source.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            source.lastError != null -> MaterialTheme.colorScheme.error
            source.lastIngestedAtMs == 0L -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            nowMs - source.lastIngestedAtMs > staleThresholdMs -> org.njarasoa.fijerena.ui.theme.CinemaWarning
            else -> org.njarasoa.fijerena.ui.theme.CinemaSuccess
        }

    androidx.compose.foundation.Canvas(modifier = Modifier.size(12.dp.scaled(scale))) {
        drawCircle(color = color)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SourceStat(
    label: String,
    value: String,
    scale: Float,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpgStatusCard(
    multiState: MultiSourceState,
    indexState: EpgIndexState,
    queuedTaskIds: Set<String>,
    lastRun: org.njarasoa.fijerena.core.network.provider.EpgPipelineStatsEntity?,
    scale: Float,
) {
    GlassPanel {
        Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
            Text(stringResource(R.string.epg_system_status), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

            // Indexer State
            val indexText =
                when (indexState) {
                    is EpgIndexState.Indexed -> stringResource(R.string.epg_database_label, stringResource(R.string.epg_database_ready, NumberUtils.formatCount(indexState.programmeCount)))
                    is EpgIndexState.Indexing -> stringResource(R.string.epg_database_label, stringResource(R.string.epg_database_indexing))
                    is EpgIndexState.Optimizing -> stringResource(R.string.epg_database_label, stringResource(R.string.epg_database_optimizing))
                    is EpgIndexState.NotIndexed -> stringResource(R.string.epg_database_label, stringResource(R.string.epg_database_empty))
                    is EpgIndexState.Failed -> stringResource(R.string.epg_database_error_prefixed, indexState.reason)
                }
            Text(indexText, style = MaterialTheme.typography.bodySmall)

            // Current Pipeline State
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
                    is MultiSourceState.Processing -> stringResource(R.string.epg_current_status, stringResource(R.string.epg_status_processing, multiState.completedCount, multiState.totalSources))
                    is MultiSourceState.Retrying -> {
                        val nextRetry = NumberUtils.formatTimestamp(LocalContext.current, multiState.nextRetryAtMs)
                        stringResource(R.string.epg_current_status, stringResource(R.string.epg_status_retrying, multiState.attempt, multiState.maxAttempts, nextRetry))
                    }
                    is MultiSourceState.Completed -> stringResource(R.string.epg_current_status, stringResource(R.string.epg_status_finished))
                    is MultiSourceState.Finalizing -> stringResource(R.string.epg_current_status, stringResource(R.string.epg_status_finalizing, localizedEpgPhase(multiState.phase)))
                    is MultiSourceState.Clearing -> stringResource(R.string.epg_current_status, stringResource(R.string.epg_status_clearing))
                    is MultiSourceState.Error -> stringResource(R.string.epg_current_status_error, multiState.reason)
                    else -> stringResource(R.string.epg_current_status, stringResource(R.string.epg_status_idle))
                }
            Text(currentStatusText, style = MaterialTheme.typography.bodySmall)

            // Last Pipeline Run
            lastRun?.let { stats ->
                val context = LocalContext.current
                val time = NumberUtils.formatTimestamp(context, stats.updatedAtMs)
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
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpgSourceEditDialog(
    initialSource: EpgSourceEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (url: String, label: String, tz: Int, method: String, enabled: Boolean) -> Unit,
    scale: Float,
) {
    var url by remember { mutableStateOf(initialSource?.url ?: "") }
    var label by remember { mutableStateOf(initialSource?.label ?: "") }
    var tzOffset by remember { mutableStateOf(initialSource?.timezoneOffsetHours?.toString() ?: "0") }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialSource == null) stringResource(R.string.epg_add_source_title) else stringResource(R.string.epg_edit_source_title), color = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary) },
        confirmButton = {
            CinemaPrimaryButton(
                onClick = {
                    onConfirm(
                        url,
                        label,
                        tzOffset.toIntOrNull() ?: 0,
                        initialSource?.ingestMethod ?: "DOWNLOADED",
                        initialSource?.enabled ?: true,
                    )
                },
                text = stringResource(R.string.provider_save_button),
                enabled = url.isNotBlank(),
            )
        },
        dismissButton = {
            CinemaSecondaryButton(onClick = onDismiss, text = stringResource(R.string.common_cancel))
        },
        containerColor = org.njarasoa.fijerena.core.ui.theme.CinemaSurface,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
                androidx.compose.material3.OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.epg_xmltv_url_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary,
                        unfocusedTextColor = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary,
                        cursorColor = org.njarasoa.fijerena.core.ui.theme.CinemaAccent,
                        focusedBorderColor = org.njarasoa.fijerena.core.ui.theme.CinemaAccent,
                        unfocusedBorderColor = org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant,
                        focusedLabelColor = org.njarasoa.fijerena.core.ui.theme.CinemaAccent,
                        unfocusedLabelColor = org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
                    )
                )
                androidx.compose.material3.OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.epg_label_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary,
                        unfocusedTextColor = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary,
                        cursorColor = org.njarasoa.fijerena.core.ui.theme.CinemaAccent,
                        focusedBorderColor = org.njarasoa.fijerena.core.ui.theme.CinemaAccent,
                        unfocusedBorderColor = org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant,
                        focusedLabelColor = org.njarasoa.fijerena.core.ui.theme.CinemaAccent,
                        unfocusedLabelColor = org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
                    )
                )
                androidx.compose.material3.OutlinedTextField(
                    value = tzOffset,
                    onValueChange = { tzOffset = it },
                    label = { Text(stringResource(R.string.epg_timezone_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary,
                        unfocusedTextColor = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary,
                        cursorColor = org.njarasoa.fijerena.core.ui.theme.CinemaAccent,
                        focusedBorderColor = org.njarasoa.fijerena.core.ui.theme.CinemaAccent,
                        unfocusedBorderColor = org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant,
                        focusedLabelColor = org.njarasoa.fijerena.core.ui.theme.CinemaAccent,
                        unfocusedLabelColor = org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
                    )
                )
            }
        },
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvTimePickerDialog(
    initialTime: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    scale: Float,
) {
    val parts = initialTime.split(":")
    var hour by remember { mutableStateOf(parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 0) }
    var minute by remember { mutableStateOf(parts.getOrNull(1)?.toIntOrNull()?.let { (it / 5) * 5 }?.coerceIn(0, 55) ?: 0) }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.epg_set_refresh_time_title), color = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeSpinnerColumn(
                    value = hour,
                    label = stringResource(R.string.epg_hour_label),
                    displayText = "%02d".format(hour),
                    onIncrement = { hour = (hour + 1) % 24 },
                    onDecrement = { hour = (hour + 23) % 24 },
                    scale = scale,
                )

                Text(
                    text = ":",
                    style = MaterialTheme.typography.displayMedium,
                    color = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary,
                    modifier = Modifier.padding(horizontal = Spacing.md.scaled(scale)),
                )

                TimeSpinnerColumn(
                    value = minute,
                    label = stringResource(R.string.epg_minute_label),
                    displayText = "%02d".format(minute),
                    onIncrement = { minute = (minute + 5) % 60 },
                    onDecrement = { minute = (minute + 55) % 60 },
                    scale = scale,
                )
            }
        },
        confirmButton = {
            CinemaPrimaryButton(
                onClick = { onConfirm("%02d:%02d".format(hour, minute)) },
                text = stringResource(R.string.provider_save_button),
            )
        },
        dismissButton = {
            CinemaSecondaryButton(onClick = onDismiss, text = stringResource(R.string.common_cancel))
        },
        containerColor = org.njarasoa.fijerena.core.ui.theme.CinemaSurface,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TimeSpinnerColumn(
    value: Int,
    label: String,
    displayText: String,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    scale: Float,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
        )
        androidx.tv.material3.Surface(
            onClick = onIncrement,
            colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                containerColor = org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant.copy(alpha = 0.5f),
                focusedContainerColor = org.njarasoa.fijerena.core.ui.theme.CinemaAccent,
                focusedContentColor = org.njarasoa.fijerena.core.ui.theme.CinemaBackground,
            ),
            scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small),
        ) {
            Icon(
                imageVector = CinemaIcons.KeyboardArrowUp,
                contentDescription = stringResource(R.string.epg_increase_description_format, label),
                modifier = Modifier.padding(Spacing.sm.scaled(scale)).size(28.dp.scaled(scale)),
            )
        }
        Text(
            text = displayText,
            style = MaterialTheme.typography.displaySmall,
            color = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary,
            modifier = Modifier.padding(vertical = Spacing.xs.scaled(scale)),
        )
        androidx.tv.material3.Surface(
            onClick = onDecrement,
            colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                containerColor = org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant.copy(alpha = 0.5f),
                focusedContainerColor = org.njarasoa.fijerena.core.ui.theme.CinemaAccent,
                focusedContentColor = org.njarasoa.fijerena.core.ui.theme.CinemaBackground,
            ),
            scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small),
        ) {
            Icon(
                imageVector = CinemaIcons.KeyboardArrowDown,
                contentDescription = stringResource(R.string.epg_decrease_description_format, label),
                modifier = Modifier.padding(Spacing.sm.scaled(scale)).size(28.dp.scaled(scale)),
            )
        }
    }
}
