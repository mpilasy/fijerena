package org.njarasoa.fijerena.feature.epg

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.network.EPG_REFRESH_INTERVAL_OPTIONS
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager.MultiSourceState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDialogActionButton
import org.njarasoa.fijerena.core.ui.components.CinemaDialogTextButton
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.*
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaTextButton
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.core.ui.R
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MobileEpgManagementScreen(onBack: () -> Unit) {
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
    var deleteSelectedIds by remember { mutableStateOf<Set<Long>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.epg_management_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CinemaIcons.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(CinemaIcons.Add, contentDescription = stringResource(R.string.epg_add_source))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(CinemaSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
            ) {
                // Quick Actions
                if (staleSourceCount > 0 || failedSourceCount > 0 || selectedIds.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
                            if (selectedIds.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
                                ) {
                                    CinemaButton(
                                        onClick = {
                                            viewModel.refreshSelected(selectedIds)
                                            viewModel.clearSelection()
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(CinemaIcons.Refresh, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                                        Text(stringResource(R.string.epg_refresh_selected_btn, selectedIds.size))
                                    }

                                    CinemaButton(
                                        onClick = { deleteSelectedIds = selectedIds },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                                    ) {
                                        Icon(CinemaIcons.Delete, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                                        Text(stringResource(R.string.epg_delete_selected_btn, selectedIds.size))
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
                            ) {
                                if (staleSourceCount > 0) {
                                    CinemaButton(
                                        onClick = { viewModel.refreshStale() },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(stringResource(R.string.epg_refresh_stale_btn, staleSourceCount))
                                    }
                                }
                                if (failedSourceCount > 0) {
                                    CinemaButton(
                                        onClick = { viewModel.refreshFailed() },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(stringResource(R.string.epg_retry_failed_btn, failedSourceCount))
                                    }
                                }
                            }
                        }
                    }
                }

                // Processing section
                item {
                    EpgStatusCard(processingState, indexState, queuedTaskIds, lastPipelineStats)
                }

                // Maintenance section
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md)) {
                        // Maintenance Card
                        GlassPanel {
                            Column(modifier = Modifier.padding(CinemaSpacing.md)) {
                                Text(stringResource(R.string.epg_maintenance_title), style = MaterialTheme.typography.titleMedium, color = CinemaAccentLight)
                                Text(
                                    stringResource(R.string.epg_maintenance_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                                )

                                Spacer(modifier = Modifier.height(CinemaSpacing.md))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
                                ) {
                                    CinemaOutlinedButton(
                                        onClick = { viewModel.cleanupFiles() },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(stringResource(R.string.epg_cleanup_btn))
                                    }
                                    CinemaOutlinedButton(
                                        onClick = { viewModel.purgeOldProgrammes() },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(stringResource(R.string.epg_purge_btn))
                                    }
                                }

                                Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                                CinemaButton(
                                    onClick = { showClearConfirm = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        CinemaIcons.DeleteForever,
                                        contentDescription = null,
                                        modifier = Modifier.size(ButtonDefaults.IconSize),
                                    )
                                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                                    Text(stringResource(R.string.epg_clear_all_data_btn))
                                }
                            }
                        }

                        // Automation Card
                        GlassPanel {
                            Column(modifier = Modifier.padding(CinemaSpacing.md)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stringResource(R.string.epg_auto_refresh_title), style = MaterialTheme.typography.titleMedium, color = CinemaAccentLight)
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
                                    Switch(
                                        checked = epgSettings.autoRefreshEnabled,
                                        onCheckedChange = { viewModel.setAutoRefreshEnabled(it) },
                                    )
                                }
                                Row(
                                    modifier = Modifier.padding(top = CinemaSpacing.sm),
                                    horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
                                ) {
                                    CinemaOutlinedButton(onClick = { showIntervalPicker = true }) {
                                        Text(when (val interval = epgSettings.epgRefreshInterval) {
                                            -1 -> stringResource(R.string.epg_automation_frequency, stringResource(R.string.epg_automation_freq_never))
                                            24 -> stringResource(R.string.epg_automation_frequency, stringResource(R.string.epg_automation_freq_daily))
                                            else -> stringResource(R.string.epg_automation_frequency, stringResource(R.string.epg_automation_freq_hours, interval))
                                        })
                                    }
                                    if (epgSettings.epgRefreshInterval != -1) {
                                        CinemaOutlinedButton(onClick = { showTimePicker = true }) {
                                            Text(stringResource(R.string.epg_automation_start_label, epgSettings.epgRefreshTime))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Source rows
                item {
                    Text(stringResource(R.string.epg_sources_header), style = MaterialTheme.typography.titleMedium, color = CinemaAccentLight)
                }

                items(sources, key = { it.id }) { source ->
                    val isSelected = selectedIds.contains(source.id)
                    val latestTime = latestProgrammeTimes[source.id] ?: 0L

                    // Look for active progress for this source
                    val activeProgress =
                        if (processingState is MultiSourceState.Processing) {
                            (processingState as MultiSourceState.Processing).activeProgress[source.id]
                        } else {
                            null
                        }

                    EpgSourceCard(
                        source = source,
                        isSelected = isSelected,
                        latestProgrammeTime = latestTime,
                        activeProgress = activeProgress,
                        nowMs = nowMs,
                        staleThresholdMs = viewModel.staleThresholdMs,
                        onRefresh = { viewModel.refreshSource(source.id) },
                        onEdit = { editingSource = source },
                        onDelete = { viewModel.deleteSource(source.id) },
                        onToggleSelection = { viewModel.toggleSelection(source.id) },
                    )
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
        )
    }

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

    deleteSelectedIds?.let { idsToDelete ->
        CinemaAlertDialog(
            onDismissRequest = { deleteSelectedIds = null },
            confirmButton = {
                CinemaDialogActionButton(
                    onClick = {
                        viewModel.deleteSelected(idsToDelete)
                        deleteSelectedIds = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                ) { Text(stringResource(R.string.epg_delete_sources_count_btn, idsToDelete.size)) }
            },
            dismissButton = {
                CinemaDialogTextButton(onClick = { deleteSelectedIds = null }) { Text(stringResource(R.string.common_cancel)) }
            },
            title = { Text(stringResource(R.string.epg_delete_selected_confirm_title)) },
            text = { Text(stringResource(R.string.epg_delete_selected_confirm_message, idsToDelete.size)) },
        )
    }

    if (showTimePicker) {
        val parts = epgSettings.epgRefreshTime.split(":")
        val timePickerState = rememberTimePickerState(
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

    if (showIntervalPicker) {
        CinemaAlertDialog(
            onDismissRequest = { showIntervalPicker = false },
            title = { Text(stringResource(R.string.epg_refresh_interval_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                    intervalOptions.forEach { interval ->
                        val label = if (interval == -1) stringResource(R.string.epg_automation_freq_never) else stringResource(R.string.epg_refresh_interval_hours_format, interval)
                        val isSelected = epgSettings.epgRefreshInterval == interval
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    viewModel.setEpgRefreshInterval(interval)
                                    if (interval == -1) {
                                        viewModel.setAutoRefreshEnabled(false)
                                    } else {
                                        viewModel.setAutoRefreshEnabled(true)
                                    }
                                    showIntervalPicker = false
                                }
                                .padding(vertical = CinemaSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(CinemaSpacing.sm))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                CinemaDialogTextButton(onClick = { showIntervalPicker = false }) { Text(stringResource(R.string.common_close)) }
            }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpgSourceCard(
    source: EpgSourceEntity,
    isSelected: Boolean,
    latestProgrammeTime: Long,
    activeProgress: org.njarasoa.fijerena.core.network.xmltv.EpgFileManager.ActiveSourceProgress?,
    nowMs: Long,
    staleThresholdMs: Long,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleSelection: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    GlassPanel(modifier = Modifier.clickable { onToggleSelection() }) {
        Column(modifier = Modifier.padding(CinemaSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
                    modifier = Modifier.weight(1f),
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelection() },
                    )

                    StatusIndicator(source, nowMs, staleThresholdMs)

                    Column {
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

                IconButton(onClick = onRefresh) {
                    Icon(CinemaIcons.Refresh, stringResource(R.string.common_refresh))
                }
            }

            if (activeProgress != null) {
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                run {
                    val lastIngested =
                        if (source.lastIngestedAtMs > 0) {
                            java.text
                                .SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(source.lastIngestedAtMs))
                        } else {
                            stringResource(R.string.epg_source_never)
                        }

                    val latestProgStr =
                        if (latestProgrammeTime > 0) {
                            java.text
                                .SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(latestProgrammeTime * 1000L))
                        } else {
                            stringResource(R.string.epg_source_none)
                        }

                    val stats =
                        listOf(
                            stringResource(R.string.epg_source_stat_label) to lastIngested,
                            stringResource(R.string.epg_source_stat_download) to NumberUtils.formatDuration(source.lastDownloadDurationMs),
                            stringResource(R.string.epg_source_stat_ingest) to NumberUtils.formatDuration(source.lastIngestionDurationMs),
                            stringResource(R.string.epg_source_stat_latest) to latestProgStr,
                            stringResource(R.string.epg_source_stat_channels) to NumberUtils.formatCount(source.lastChannels),
                            stringResource(R.string.epg_source_stat_programmes) to NumberUtils.formatCount(source.lastProgrammes),
                        )
                    // Fixed 6-item stat grid, chunked into rows instead of FlowRow — see
                    // MatchTypeChipRow note in tv/ProviderDialogs.kt for why.
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
                    ) {
                        stats.chunked(3).forEach { rowStats ->
                            Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.md)) {
                                rowStats.forEach { (label, value) -> SourceStat(label, value) }
                            }
                        }
                    }
                }
            }

            val lastError = source.lastError
            if (lastError != null) {
                Text(
                    text = stringResource(R.string.epg_database_error, lastError),
                    style = MaterialTheme.typography.labelSmall,
                    color = CinemaError,
                    modifier = Modifier.padding(top = CinemaSpacing.xs),
                )
            }

            Spacer(modifier = Modifier.height(CinemaSpacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CinemaTextButton(onClick = onEdit) {
                    Text(stringResource(R.string.provider_edit_button))
                }
                CinemaTextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = CinemaError),
                ) {
                    Text(stringResource(R.string.provider_delete_button))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        CinemaAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                CinemaDialogActionButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                ) { Text(stringResource(R.string.provider_delete_button)) }
            },
            dismissButton = {
                CinemaDialogTextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            },
            title = { Text(stringResource(R.string.epg_delete_source_title)) },
            text = { Text(stringResource(R.string.epg_delete_source_confirm_message, source.label)) },
        )
    }
}

@Composable
private fun StatusIndicator(
    source: EpgSourceEntity,
    nowMs: Long,
    staleThresholdMs: Long,
) {
    val color =
        when {
            !source.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            source.lastError != null -> CinemaError
            source.lastIngestedAtMs == 0L -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            nowMs - source.lastIngestedAtMs > staleThresholdMs -> org.njarasoa.fijerena.ui.theme.CinemaWarning
            else -> CinemaSuccess
        }

    androidx.compose.foundation.Canvas(modifier = Modifier.size(10.dp)) {
        drawCircle(color = color)
    }
}

@Composable
private fun SourceStat(
    label: String,
    value: String,
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

@Composable
private fun EpgStatusCard(
    multiState: MultiSourceState,
    indexState: EpgIndexState,
    queuedTaskIds: Set<String>,
    lastRun: org.njarasoa.fijerena.core.network.provider.EpgPipelineStatsEntity?,
) {
    GlassPanel {
        Column(modifier = Modifier.padding(CinemaSpacing.md)) {
            Text(stringResource(R.string.epg_system_status), style = MaterialTheme.typography.titleMedium, color = CinemaAccentLight)
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))

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
                        if (queued > 0) stringResource(R.string.epg_current_status, stringResource(R.string.epg_status_tasks_queued, queued)) else stringResource(R.string.epg_current_status, stringResource(R.string.epg_status_idle))
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

@Composable
private fun EpgSourceEditDialog(
    initialSource: EpgSourceEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (url: String, label: String, tz: Int, method: String, enabled: Boolean) -> Unit,
) {
    var url by remember { mutableStateOf(initialSource?.url ?: "") }
    var label by remember { mutableStateOf(initialSource?.label ?: "") }
    var tzOffset by remember { mutableStateOf(initialSource?.timezoneOffsetHours?.toString() ?: "0") }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialSource == null) stringResource(R.string.epg_add_source_title) else stringResource(R.string.epg_edit_source_title)) },
        confirmButton = {
            CinemaDialogActionButton(
                onClick = {
                    onConfirm(
                        url,
                        label,
                        tzOffset.toIntOrNull() ?: 0,
                        initialSource?.ingestMethod ?: "DOWNLOADED",
                        initialSource?.enabled ?: true,
                    )
                },
                enabled = url.isNotBlank(),
            ) { Text(stringResource(R.string.provider_save_button)) }
        },
        dismissButton = {
            CinemaDialogTextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.epg_xmltv_url_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.epg_label_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = tzOffset,
                    onValueChange = { tzOffset = it },
                    label = { Text(stringResource(R.string.epg_timezone_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
    )
}
