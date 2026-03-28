package org.njarasoa.fijerena.feature.epg

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager.MultiSourceState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.*
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory

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

    val nowMs = remember { System.currentTimeMillis() }

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { message ->
            android.widget.Toast
                .makeText(context, message, android.widget.Toast.LENGTH_SHORT)
                .show()
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<EpgSourceEntity?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var deleteSelectedIds by remember { mutableStateOf<Set<Long>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EPG Management") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Source")
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
                                    Button(
                                        onClick = {
                                            viewModel.refreshSelected(selectedIds)
                                            viewModel.clearSelection()
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                                        Text("Refresh (${selectedIds.size})")
                                    }

                                    Button(
                                        onClick = { deleteSelectedIds = selectedIds },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                                    ) {
                                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                                        Text("Delete (${selectedIds.size})")
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
                            ) {
                                if (staleSourceCount > 0) {
                                    Button(
                                        onClick = { viewModel.refreshStale() },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("Refresh Stale ($staleSourceCount)")
                                    }
                                }
                                if (failedSourceCount > 0) {
                                    Button(
                                        onClick = { viewModel.refreshFailed() },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("Retry Failed ($failedSourceCount)")
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
                                Text("Maintenance", style = MaterialTheme.typography.titleMedium, color = CinemaAccentLight)
                                Text(
                                    "Manage local database and temporary files.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                                )

                                Spacer(modifier = Modifier.height(CinemaSpacing.md))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.cleanupFiles() },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("Cleanup")
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.purgeOldProgrammes() },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("Purge")
                                    }
                                }

                                Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                                Button(
                                    onClick = { showClearConfirm = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        Icons.Default.DeleteForever,
                                        contentDescription = null,
                                        modifier = Modifier.size(ButtonDefaults.IconSize),
                                    )
                                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                                    Text("Clear All Data")
                                }
                            }
                        }

                        // Automation Card
                        GlassPanel {
                            Row(
                                modifier =
                                    Modifier
                                        .padding(CinemaSpacing.md)
                                        .clickable { showTimePicker = true },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Auto-Refresh", style = MaterialTheme.typography.titleMedium, color = CinemaAccentLight)
                                    Text(
                                        "Daily update at ${viewModel.epgRefreshTime}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                                    )
                                }
                                Switch(
                                    checked = viewModel.autoRefreshEnabled,
                                    onCheckedChange = { viewModel.setAutoRefreshEnabled(it) },
                                )
                            }
                        }
                    }
                }

                // Source rows
                item {
                    Text("Sources", style = MaterialTheme.typography.titleMedium, color = CinemaAccentLight)
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
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearDatabase()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                ) { Text("Clear Everything") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            },
            title = { Text("Clear EPG Data?") },
            text = { Text("This will delete all indexed programmes and channels. Your source URLs will be preserved.") },
        )
    }

    deleteSelectedIds?.let { idsToDelete ->
        AlertDialog(
            onDismissRequest = { deleteSelectedIds = null },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelected(idsToDelete)
                        deleteSelectedIds = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                ) { Text("Delete ${idsToDelete.size} Source(s)") }
            },
            dismissButton = {
                TextButton(onClick = { deleteSelectedIds = null }) { Text("Cancel") }
            },
            title = { Text("Delete Selected Sources?") },
            text = { Text("This will permanently remove ${idsToDelete.size} source(s) and all their indexed data.") },
        )
    }

    if (showTimePicker) {
        var timeInput by remember { mutableStateOf(viewModel.epgRefreshTime) }
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Set Refresh Time") },
            text = {
                OutlinedTextField(
                    value = timeInput,
                    onValueChange = { timeInput = it },
                    label = { Text("Time (HH:mm)") },
                    placeholder = { Text("e.g. 04:00") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setEpgRefreshTime(timeInput)
                        showTimePicker = false
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpgSourceCard(
    source: EpgSourceEntity,
    isSelected: Boolean,
    latestProgrammeTime: Long,
    activeProgress: org.njarasoa.fijerena.core.network.xmltv.EpgFileManager.ActiveSourceProgress?,
    nowMs: Long,
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

                    StatusIndicator(source, nowMs)

                    Column {
                        Text(
                            text = source.label.ifBlank { "Unnamed Source" },
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
                    Icon(Icons.Default.Refresh, "Refresh")
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
                            text = "${activeProgress.phase}...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "${activeProgress.progressPercent}%",
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

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
                ) {
                    val lastIngested =
                        if (source.lastIngestedAtMs > 0) {
                            java.text
                                .SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(source.lastIngestedAtMs))
                        } else {
                            "Never"
                        }

                    val latestProgStr =
                        if (latestProgrammeTime > 0) {
                            java.text
                                .SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(latestProgrammeTime * 1000L))
                        } else {
                            "None"
                        }

                    SourceStat("Last Sync", lastIngested)
                    SourceStat("Download", NumberUtils.formatDuration(source.lastDownloadDurationMs))
                    SourceStat("Ingest", NumberUtils.formatDuration(source.lastIngestionDurationMs))
                    SourceStat("Latest Prog", latestProgStr)
                    SourceStat("Channels", NumberUtils.formatCount(source.lastChannels))
                    SourceStat("Programmes", NumberUtils.formatCount(source.lastProgrammes))
                }
            }

            if (source.lastError != null) {
                Text(
                    text = "Error: ${source.lastError}",
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
                TextButton(onClick = onEdit) {
                    Text("Edit")
                }
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = CinemaError),
                ) {
                    Text("Delete")
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
            title = { Text("Delete Source?") },
            text = { Text("Are you sure you want to remove '${source.label}'? All associated EPG data will be deleted.") },
        )
    }
}

@Composable
private fun StatusIndicator(
    source: EpgSourceEntity,
    nowMs: Long,
) {
    val color =
        when {
            !source.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            source.lastError != null -> CinemaError
            source.lastIngestedAtMs == 0L -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            nowMs - source.lastIngestedAtMs > 24 * 3600 * 1000 -> org.njarasoa.fijerena.ui.theme.CinemaWarning
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
            Text("System Status", style = MaterialTheme.typography.titleMedium, color = CinemaAccentLight)
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))

            // Indexer State
            val indexText =
                when (indexState) {
                    is EpgIndexState.Indexed -> "Database: ${NumberUtils.formatCount(indexState.programmeCount)} programmes indexed"
                    is EpgIndexState.Indexing -> "Database: Indexing in progress..."
                    is EpgIndexState.NotIndexed -> "Database: Empty"
                    is EpgIndexState.Failed -> "Database Error: ${indexState.reason}"
                }
            Text(indexText, style = MaterialTheme.typography.bodySmall)

            // Current Pipeline State
            val currentStatusText =
                when (multiState) {
                    is MultiSourceState.Idle -> {
                        val queued = queuedTaskIds.count { it.startsWith("epg_refresh_") }
                        if (queued > 0) "Current Status: $queued refresh tasks queued" else "Current Status: Idle"
                    }
                    is MultiSourceState.Processing -> "Current Status: Processing ${multiState.completedCount}/${multiState.totalSources} sources"
                    is MultiSourceState.Completed -> "Current Status: Finished run"
                    is MultiSourceState.Finalizing -> "Current Status: Finalizing (${multiState.phase})..."
                    is MultiSourceState.Clearing -> "Current Status: Clearing data..."
                    is MultiSourceState.Error -> "Current Status Error: ${multiState.reason}"
                    else -> "Current Status: Idle"
                }
            Text(currentStatusText, style = MaterialTheme.typography.bodySmall)

            // Last Pipeline Run
            lastRun?.let { stats ->
                val context = LocalContext.current
                val time = NumberUtils.formatTimestamp(context, stats.updatedAtMs)
                val duration = NumberUtils.formatDuration(stats.durationMs)
                val errorText = if (stats.errors > 0) " (${stats.errors} errors)" else ""
                Text(
                    text = "Last Run: Finished at $time • ${stats.sourcesProcessed} sources in $duration$errorText",
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialSource == null) "Add EPG Source" else "Edit EPG Source") },
        confirmButton = {
            Button(
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
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("XMLTV URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = tzOffset,
                    onValueChange = { tzOffset = it },
                    label = { Text("Timezone Offset (Hours)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
    )
}
