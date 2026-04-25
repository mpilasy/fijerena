package org.njarasoa.fijerena.feature.epg

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
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
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager.MultiSourceState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

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
                text = "EPG Management",
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
                            text = "Add Source",
                        )

                        if (selectedIds.isNotEmpty()) {
                            CinemaSecondaryButton(
                                onClick = {
                                    viewModel.refreshSelected(selectedIds)
                                    viewModel.clearSelection()
                                },
                                text = "Refresh Selected (${selectedIds.size})",
                            )

                            CinemaDangerButton(
                                onClick = { deleteSelectedIds = selectedIds },
                                text = "Delete Selected (${selectedIds.size})",
                            )
                        }

                        if (staleSourceCount > 0) {
                            CinemaSecondaryButton(
                                onClick = { viewModel.refreshStale() },
                                text = "Refresh Stale ($staleSourceCount)",
                            )
                        }

                        if (failedSourceCount > 0) {
                            CinemaSecondaryButton(
                                onClick = { viewModel.refreshFailed() },
                                text = "Retry Failed ($failedSourceCount)",
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
                                    Text("Maintenance", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Clean up temporary files and old programmes.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                                    )
                                }

                                CinemaSecondaryButton(
                                    onClick = { viewModel.cleanupFiles() },
                                    text = "Cleanup",
                                )

                                CinemaSecondaryButton(
                                    onClick = { viewModel.purgeOldProgrammes() },
                                    text = "Purge",
                                )

                                CinemaDangerButton(
                                    onClick = { showClearConfirm = true },
                                    text = "Clear All",
                                )
                            }
                        }

                        // Automation Card
                        GlassPanel(modifier = Modifier.weight(0.6f)) {
                            Row(
                                modifier = Modifier.padding(Spacing.md.scaled(scale)),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .clickable { showTimePicker = true },
                                ) {
                                    Text("Auto-Refresh", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Daily update at ${viewModel.epgRefreshTime}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                                    )
                                }

                                androidx.tv.material3.Surface(
                                    checked = viewModel.autoRefreshEnabled,
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
                                        onCheckedChange = null, // Handled by Surface
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
                                            imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                            contentDescription = if (isSelected) "Selected" else "Not selected",
                                            modifier = Modifier.size(24.dp.scaled(scale)),
                                        )
                                    }
                                }

                                StatusIndicator(source, nowMs, scale)

                                Column(modifier = Modifier.weight(1f)) {
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

                            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
                                CinemaSecondaryButton(
                                    onClick = { viewModel.refreshSource(source.id) },
                                    text = "Refresh",
                                )
                                CinemaSecondaryButton(
                                    onClick = { editingSource = source },
                                    text = "Edit",
                                )
                                CinemaDangerButton(
                                    onClick = { deletingSource = source },
                                    text = "Delete",
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
                                            "Never"
                                        }

                                    val latestProgStr =
                                        if (latestTime > 0) {
                                            java.text
                                                .SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                                                .format(java.util.Date(latestTime * 1000L))
                                        } else {
                                            "None"
                                        }

                                    SourceStat("Last Sync", lastIngested, scale)
                                    SourceStat("Download", NumberUtils.formatDuration(source.lastDownloadDurationMs), scale)
                                    SourceStat("Ingest", NumberUtils.formatDuration(source.lastIngestionDurationMs), scale)
                                    SourceStat("Latest Prog", latestProgStr, scale)
                                    SourceStat("Channels", NumberUtils.formatCount(source.lastChannels), scale)
                                    SourceStat("Programmes", NumberUtils.formatCount(source.lastProgrammes), scale)

                                    if (source.lastError != null) {
                                        Text(
                                            text = "Error: ${source.lastError}",
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
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            confirmButton = {
                CinemaDangerButton(
                    onClick = {
                        viewModel.clearDatabase()
                        showClearConfirm = false
                    },
                    text = "Clear Everything",
                )
            },
            dismissButton = {
                CinemaSecondaryButton(
                    onClick = { showClearConfirm = false },
                    text = "Cancel",
                )
            },
            title = { Text("Clear EPG Data?", color = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary) },
            text = { Text("This will delete all indexed programmes and channels. Your source URLs will be preserved.", color = org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary) },
            containerColor = org.njarasoa.fijerena.core.ui.theme.CinemaSurface,
        )
    }

    deletingSource?.let { source ->
        AlertDialog(
            onDismissRequest = { deletingSource = null },
            confirmButton = {
                CinemaDangerButton(
                    onClick = {
                        viewModel.deleteSource(source.id)
                        deletingSource = null
                    },
                    text = "Delete",
                )
            },
            dismissButton = {
                CinemaSecondaryButton(
                    onClick = { deletingSource = null },
                    text = "Cancel",
                )
            },
            title = { Text("Delete EPG Source?", color = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary) },
            text = { Text("This will remove \"${source.label.ifBlank { source.url }}\" and all its indexed data.", color = org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary) },
            containerColor = org.njarasoa.fijerena.core.ui.theme.CinemaSurface,
        )
    }

    deleteSelectedIds?.let { idsToDelete ->
        AlertDialog(
            onDismissRequest = { deleteSelectedIds = null },
            confirmButton = {
                CinemaDangerButton(
                    onClick = {
                        viewModel.deleteSelected(idsToDelete)
                        deleteSelectedIds = null
                    },
                    text = "Delete ${idsToDelete.size} Source(s)",
                )
            },
            dismissButton = {
                CinemaSecondaryButton(
                    onClick = { deleteSelectedIds = null },
                    text = "Cancel",
                )
            },
            title = { Text("Delete Selected Sources?", color = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary) },
            text = { Text("This will permanently remove ${idsToDelete.size} source(s) and all their indexed data.", color = org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary) },
            containerColor = org.njarasoa.fijerena.core.ui.theme.CinemaSurface,
        )
    }

    if (showTimePicker) {
        var timeInput by remember { mutableStateOf(viewModel.epgRefreshTime) }
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Set Refresh Time", color = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = timeInput,
                    onValueChange = { timeInput = it },
                    label = { Text("Time (HH:mm)") },
                    placeholder = { Text("e.g. 04:00") },
                    modifier = Modifier.fillMaxWidth(),
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
            },
            confirmButton = {
                CinemaPrimaryButton(
                    onClick = {
                        viewModel.setEpgRefreshTime(timeInput)
                        showTimePicker = false
                    },
                    text = "Save",
                )
            },
            dismissButton = {
                CinemaSecondaryButton(onClick = { showTimePicker = false }, text = "Cancel")
            },
            containerColor = org.njarasoa.fijerena.core.ui.theme.CinemaSurface,
        )
    }
}

@Composable
private fun StatusIndicator(
    source: EpgSourceEntity,
    nowMs: Long,
    scale: Float,
) {
    val color =
        when {
            !source.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            source.lastError != null -> MaterialTheme.colorScheme.error
            source.lastIngestedAtMs == 0L -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            nowMs - source.lastIngestedAtMs > 24 * 3600 * 1000 -> org.njarasoa.fijerena.ui.theme.CinemaWarning
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
            Text("System Status", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialSource == null) "Add EPG Source" else "Edit EPG Source", color = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary) },
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
                text = "Save",
                enabled = url.isNotBlank(),
            )
        },
        dismissButton = {
            CinemaSecondaryButton(onClick = onDismiss, text = "Cancel")
        },
        containerColor = org.njarasoa.fijerena.core.ui.theme.CinemaSurface,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
                androidx.compose.material3.OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("XMLTV URL") },
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
                    label = { Text("Label (Optional)") },
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
                    label = { Text("Timezone Offset (Hours)") },
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
