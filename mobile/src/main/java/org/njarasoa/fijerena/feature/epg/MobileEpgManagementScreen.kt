package org.njarasoa.fijerena.feature.epg

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgSourceEntity
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModelFactory
import org.njarasoa.fijerena.ui.theme.CinemaError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileEpgManagementScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: EpgManagementViewModel = viewModel(
        factory = remember { EpgManagementViewModelFactory(context.applicationContext) }
    )

    val sources by viewModel.sources.collectAsState(initial = emptyList())
    val processingState by viewModel.processingState.collectAsState()
    val indexState by viewModel.indexState.collectAsState()
    val dbStats by viewModel.dbStats.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<EpgSourceEntity?>(null) }
    var autoRefreshEnabled by remember { mutableStateOf(viewModel.autoRefreshEnabled) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EPG Management") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(CinemaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md)
        ) {
            // Status
            SettingsSection(title = "Status") {
                val statusText = when (val state = indexState) {
                    is EpgIndexState.NotIndexed -> "No EPG data"
                    is EpgIndexState.Indexing -> "Indexing: ${state.progressPercent}%"
                    is EpgIndexState.Indexed -> "${formatCount(state.programmeCount)} programmes, ${formatCount(state.channelCount)} channels"
                    is EpgIndexState.Failed -> "Failed: ${state.reason}"
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (indexState) {
                        is EpgIndexState.Indexed -> MaterialTheme.colorScheme.primary
                        is EpgIndexState.Failed -> CinemaError
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                val procState = processingState
                if (procState is EpgFileManager.MultiSourceState.Processing) {
                    Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                    Text(
                        text = "Source ${procState.sourceIndex}/${procState.totalSources}: ${procState.phase} ${procState.sourceLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (viewModel.isDevMode) {
                        if (procState.phase == "Downloading" && procState.downloadedBytes > 0) {
                            val progress = if (procState.downloadTotalBytes > 0) {
                                "${formatBytes(procState.downloadedBytes)} / ${formatBytes(procState.downloadTotalBytes)}" +
                                    " (${(procState.downloadedBytes * 100 / procState.downloadTotalBytes)}%)"
                            } else {
                                formatBytes(procState.downloadedBytes)
                            }
                            Text(
                                text = "Download: $progress",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (procState.completedSourceStats.isNotEmpty()) {
                            procState.completedSourceStats.forEach { stat ->
                                Text(
                                    text = "${stat.label}: ${formatBytes(stat.downloadBytes)}, ${formatCount(stat.channelsIngested)}ch, ${formatCount(stat.programmesIngested)}prg" +
                                        (stat.error?.let { " [$it]" } ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (stat.error != null) CinemaError else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                if (procState is EpgFileManager.MultiSourceState.Completed) {
                    Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                    Text(
                        text = "Completed: ${procState.sourcesProcessed} sources" +
                            if (procState.errors > 0) " (${procState.errors} errors)" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (procState.errors > 0) CinemaError else MaterialTheme.colorScheme.primary
                    )
                    if (viewModel.isDevMode) {
                        Text(
                            text = "Total: ${formatBytes(procState.totalDownloadBytes)}, ${formatCount(procState.totalChannels)}ch, ${formatCount(procState.totalProgrammes)}prg",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        procState.sourceStats.forEach { stat ->
                            Text(
                                text = "${stat.label}: ${formatBytes(stat.downloadBytes)}, ${formatCount(stat.channelsIngested)}ch, ${formatCount(stat.programmesIngested)}prg" +
                                    (stat.error?.let { " [$it]" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (stat.error != null) CinemaError else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (procState is EpgFileManager.MultiSourceState.Error) {
                    Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                    Text(
                        text = procState.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = CinemaError
                    )
                }
            }

            // Sources
            SettingsSection(title = "Sources (${sources.size})") {
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Source")
                }

                sources.forEach { source ->
                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val dotColor = when {
                            !source.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                            source.lastError != null -> CinemaError
                            source.lastIngestedAtMs > 0 && (System.currentTimeMillis() - source.lastIngestedAtMs) < 24 * 3600 * 1000 -> MaterialTheme.colorScheme.primary
                            source.lastIngestedAtMs > 0 -> androidx.compose.ui.graphics.Color(0xFFFFAB40)
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                        }
                        Surface(
                            modifier = Modifier.size(CinemaSpacing.sm),
                            shape = CircleShape,
                            color = dotColor
                        ) {}

                        Spacer(modifier = Modifier.width(CinemaSpacing.sm))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = source.label.ifBlank { EpgFileManager.extractLabel(source.url) },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = source.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                                maxLines = 1
                            )
                            val tzLabel = if (source.timezoneOffsetHours == 0) "Auto" else {
                                val sign = if (source.timezoneOffsetHours >= 0) "+" else ""
                                "UTC${sign}${source.timezoneOffsetHours}"
                            }
                            Text(
                                text = "TZ: $tzLabel" +
                                    (if (source.lastIngestedAtMs > 0) " | ${formatTimestamp(source.lastIngestedAtMs)}" else "") +
                                    (if (!source.enabled) " | DISABLED" else ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                            )
                            source.lastError?.let { error ->
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CinemaError,
                                    maxLines = 1
                                )
                            }
                            if (viewModel.isDevMode && source.lastIngestedAtMs > 0) {
                                Text(
                                    text = "${formatCount(source.lastChannels)}ch, ${formatCount(source.lastProgrammes)}prg, ${formatBytes(source.lastDownloadBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                                )
                                var latestTime by remember { mutableStateOf<Long?>(null) }
                                LaunchedEffect(source.id, source.lastIngestedAtMs) {
                                    latestTime = viewModel.getLatestProgrammeTime(source.id)
                                }
                                latestTime?.let { epoch ->
                                    Text(
                                        text = "Latest: ${formatEpochDate(epoch)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm, Alignment.End)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.refreshSource(source.id) },
                            enabled = processingState !is EpgFileManager.MultiSourceState.Processing
                        ) {
                            Text("Refresh")
                        }
                        OutlinedButton(onClick = { editingSource = source }) {
                            Text("Edit")
                        }
                        OutlinedButton(
                            onClick = { showDeleteConfirm = source.id },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CinemaError)
                        ) {
                            Text("Delete")
                        }
                    }
                }
            }

            // Actions
            SettingsSection(title = "Actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Auto-refresh (every 24h)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = autoRefreshEnabled,
                        onCheckedChange = {
                            autoRefreshEnabled = it
                            viewModel.setAutoRefreshEnabled(it)
                        }
                    )
                }
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                Button(
                    onClick = { viewModel.refreshAll() },
                    enabled = sources.isNotEmpty() && processingState !is EpgFileManager.MultiSourceState.Processing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Refresh All Sources")
                }
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.cleanupFiles() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cleanup")
                    }
                    OutlinedButton(
                        onClick = { viewModel.purgeOldProgrammes() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Purge >7d")
                    }
                }
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                Button(
                    onClick = { showClearConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear All Data")
                }
            }

            Spacer(modifier = Modifier.height(CinemaSpacing.md))
        }
    }

    // Add/Edit Source Dialog
    if (showAddDialog || editingSource != null) {
        MobileSourceDialog(
            source = editingSource,
            onDismiss = {
                showAddDialog = false
                editingSource = null
            },
            onSave = { url, label, tz ->
                if (editingSource != null) {
                    viewModel.updateSource(
                        editingSource!!.copy(url = url, label = label, timezoneOffsetHours = tz)
                    )
                } else {
                    viewModel.addSource(url, label, tz)
                }
                showAddDialog = false
                editingSource = null
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear All EPG Data") },
            text = { Text("This will delete all indexed programmes and channels. Sources will be kept.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearDatabase()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaError)
                ) { Text("Clear") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }

    showDeleteConfirm?.let { sourceId ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Source") },
            text = { Text("Remove this EPG source?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSource(sourceId)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaError)
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CinemaSpacing.md)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            content()
        }
    }
}

@Composable
private fun MobileSourceDialog(
    source: EpgSourceEntity?,
    onDismiss: () -> Unit,
    onSave: (url: String, label: String, tz: Int) -> Unit
) {
    var url by remember { mutableStateOf(source?.url ?: "") }
    var label by remember { mutableStateOf(source?.label ?: "") }
    var tzOffset by remember { mutableIntStateOf(source?.timezoneOffsetHours ?: 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (source != null) "Edit Source" else "Add Source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(if (source != null) "XMLTV URL" else "XMLTV URL(s)") },
                    placeholder = { Text(if (source != null) "https://epg.example.com/guide.xml.gz" else "One or more URLs (one per line)") },
                    singleLine = source != null,
                    maxLines = if (source != null) 1 else 5,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (optional)") },
                    placeholder = { Text("Auto-detected from URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Timezone:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.width(CinemaSpacing.sm))
                    val tzLabel = if (tzOffset == 0) "Auto (from data)" else {
                        val sign = if (tzOffset >= 0) "+" else ""
                        "UTC${sign}${tzOffset}"
                    }
                    OutlinedButton(onClick = {
                        tzOffset = (tzOffset + 1).let { if (it > 14) -12 else it }
                    }) {
                        Text(tzLabel)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(url, label, tzOffset) },
                enabled = url.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> "%.1fGB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1fMB".format(bytes / 1_048_576.0)
        bytes >= 1_024 -> "%.1fKB".format(bytes / 1_024.0)
        else -> "${bytes}B"
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
        count >= 1_000 -> "%.1fK".format(count / 1_000.0)
        else -> count.toString()
    }
}

private fun formatTimestamp(millis: Long): String {
    val format = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
    format.timeZone = java.util.TimeZone.getDefault()
    return format.format(java.util.Date(millis))
}

private fun formatEpochDate(epochSeconds: Long): String {
    val format = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
    format.timeZone = java.util.TimeZone.getDefault()
    return format.format(java.util.Date(epochSeconds * 1000L))
}
