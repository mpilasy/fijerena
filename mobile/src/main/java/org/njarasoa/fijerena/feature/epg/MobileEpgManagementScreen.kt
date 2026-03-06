package org.njarasoa.fijerena.feature.epg

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh

import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

    val sources by viewModel.sources.collectAsStateWithLifecycle(initialValue = emptyList())
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()
    val indexState by viewModel.indexState.collectAsStateWithLifecycle()
    val dbStats by viewModel.dbStats.collectAsStateWithLifecycle()
    val queuedTaskIds by viewModel.queuedTaskIds.collectAsStateWithLifecycle()
    val hasStrayFiles by viewModel.hasStrayFiles.collectAsStateWithLifecycle()
    val staleProgrammeCount by viewModel.staleProgrammeCount.collectAsStateWithLifecycle()

    val nowMs = remember { System.currentTimeMillis() }

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<EpgSourceEntity?>(null) }
    var autoRefreshEnabled by remember { mutableStateOf(viewModel.autoRefreshEnabled) }
    var epgRefreshTime by remember { mutableStateOf(viewModel.epgRefreshTime) }
    var showTimeDialog by remember { mutableStateOf(false) }
    var showCleanupConfirm by remember { mutableStateOf(false) }
    var showPurgeConfirm by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<Long?>(null) }
    var selectedSourceIds by remember { mutableStateOf(emptySet<Long>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EPG Management") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Source")
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
                    is EpgIndexState.Indexed -> "${formatCount(state.channelCount)} channels, ${formatCount(state.programmeCount)} programmes"
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
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${procState.completedCount}/${procState.totalSources} sources",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.cancelProcessing() }) {
                            Text("Cancel", style = MaterialTheme.typography.labelSmall, color = CinemaError)
                        }
                    }
                    // Per-source active progress
                    procState.activeProgress.forEach { progress ->
                        val progressText = buildString {
                            append(progress.label)
                            append(": ")
                            append(progress.phase)
                            if (progress.progressPercent in 0..100) {
                                append(" ${progress.progressPercent}%")
                            }
                            if (progress.phase == "Downloading" || progress.phase == "Awaiting Ingestion") {
                                append(" (${formatBytes(progress.downloadedBytes)}")
                                if (progress.downloadTotalBytes > 0) {
                                    append("/${formatBytes(progress.downloadTotalBytes)}")
                                }
                                append(")")
                            } else if (progress.programmes > 0) {
                                append(" (${formatCount(progress.channels)}ch, ${formatCount(progress.programmes)}prg)")
                            }
                        }
                        Text(
                            text = progressText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (procState.totalChannels > 0 || procState.totalProgrammes > 0) {
                        Text(
                            text = "Total: ${formatCount(procState.totalChannels)}ch, ${formatCount(procState.totalProgrammes)}prg",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (viewModel.isDevMode && procState.completedSourceStats.isNotEmpty()) {
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
                sources.forEach { source ->
                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isSelected = source.id in selectedSourceIds
                        IconButton(
                            onClick = {
                                selectedSourceIds = if (isSelected) selectedSourceIds - source.id
                                else selectedSourceIds + source.id
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = "Select",
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(CinemaSpacing.xs))

                        val isQueued = queuedTaskIds.contains("epg_refresh_source_${source.id}") || queuedTaskIds.contains("epg_refresh_all")

                        val dotColor = when {
                            isQueued -> androidx.compose.ui.graphics.Color.Yellow
                            !source.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                            source.lastError != null -> CinemaError
                            source.lastIngestedAtMs > 0 && (nowMs - source.lastIngestedAtMs) < 24 * 3600 * 1000 -> MaterialTheme.colorScheme.primary
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
                                    (if (source.lastIngestedAtMs > 0) " | ${formatTimestamp(context, source.lastIngestedAtMs)}" else "") +
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
                                val sizeStr = if (source.ingestMethod != "STREAMED") {
                                    ", ${formatBytes(source.lastDownloadBytes)}"
                                } else {
                                    ""
                                }
                                Text(
                                    text = "${formatCount(source.lastChannels)}ch, ${formatCount(source.lastProgrammes)}prg$sizeStr",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                                )
                                var latestTime by remember { mutableStateOf<Long?>(null) }
                                LaunchedEffect(source.id, source.lastIngestedAtMs) {
                                    latestTime = viewModel.getLatestProgrammeTime(source.id)
                                }
                                latestTime?.let { epoch ->
                                    Text(
                                        text = "Latest: ${formatEpochDate(context, epoch)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs, Alignment.End)
                    ) {
                        IconButton(onClick = { viewModel.refreshSource(source.id) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = { editingSource = source }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { showDeleteConfirm = source.id }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CinemaError)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Refresh Time",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(onClick = { showTimeDialog = true }) {
                        Text(epgRefreshTime)
                    }
                }
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)
                ) {
                    Button(
                        onClick = { viewModel.refreshAll() },
                        enabled = sources.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Refresh All")
                    }
                    if (selectedSourceIds.isNotEmpty()) {
                        Button(
                            onClick = {
                                viewModel.refreshSelected(selectedSourceIds)
                                selectedSourceIds = emptySet()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                            Text("Selected (${selectedSourceIds.size})")
                        }
                    }
                }
                if (viewModel.isDevMode) {
                    val hasFailed = sources.any { it.enabled && it.lastError != null }
                    val hasOutdated = sources.any { it.enabled && (it.lastIngestedAtMs == 0L || (nowMs - it.lastIngestedAtMs) > 24 * 3600 * 1000) }
                    if (hasFailed || hasOutdated) {
                        Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)
                        ) {
                            if (hasFailed) {
                                OutlinedButton(
                                    onClick = { viewModel.refreshFailed() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                                    Text("Failed")
                                }
                            }
                            if (hasOutdated) {
                                OutlinedButton(
                                    onClick = { viewModel.refreshOutdated() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Update, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                                    Text("Outdated")
                                }
                            }
                        }
                    }
                }
                if (hasStrayFiles || staleProgrammeCount > 0) {
                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)
                    ) {
                        if (hasStrayFiles) {
                            OutlinedButton(
                                onClick = { showCleanupConfirm = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                                Text("Cleanup")
                            }
                        }
                        if (staleProgrammeCount > 0) {
                            OutlinedButton(
                                onClick = { showPurgeConfirm = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                                Text("Purge >2d")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                Button(
                    onClick = { showClearConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
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

    if (showTimeDialog) {
        MobileTimeDialog(
            currentTime = epgRefreshTime,
            onDismiss = { showTimeDialog = false },
            onSave = { newTime ->
                epgRefreshTime = newTime
                viewModel.setEpgRefreshTime(newTime)
                showTimeDialog = false
            }
        )
    }

    if (showCleanupConfirm) {
        AlertDialog(
            onDismissRequest = { showCleanupConfirm = false },
            title = { Text("Cleanup Files") },
            text = { Text("This will delete any downloaded EPG files that are no longer associated with a source. The indexed data in the database is not affected.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cleanupFiles()
                        showCleanupConfirm = false
                    }
                ) { Text("Cleanup") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCleanupConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showPurgeConfirm) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirm = false },
            title = { Text("Purge Old Programmes") },
            text = { Text("This will permanently delete all programme data older than 2 days from the database. Channel entries are not affected.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.purgeOldProgrammes()
                        showPurgeConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaError)
                ) { Text("Purge") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPurgeConfirm = false }) { Text("Cancel") }
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

    // Blocking overlay while clearing data
    if (processingState is EpgFileManager.MultiSourceState.Clearing) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Box(modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(CinemaSpacing.md))
                Text(
                    text = "Clearing all EPG data...",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                Text(
                    text = "This may take a moment",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

    )
}

@Composable
private fun MobileTimeDialog(
    currentTime: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val initialHour = currentTime.substringBefore(":").toIntOrNull() ?: 2
    val initialMinute = currentTime.substringAfter(":").toIntOrNull() ?: 0
    var hour by remember { mutableIntStateOf(initialHour) }
    var minute by remember { mutableIntStateOf(initialMinute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("EPG Refresh Time") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { hour = (hour + 1) % 24 }) {
                        Icon(androidx.compose.material.icons.filled.KeyboardArrowUp, "Hour Up")
                    }
                    Text(
                        text = "%02d".format(hour),
                        style = MaterialTheme.typography.displayMedium
                    )
                    IconButton(onClick = { hour = if (hour == 0) 23 else hour - 1 }) {
                        Icon(androidx.compose.material.icons.filled.KeyboardArrowDown, "Hour Down")
                    }
                }
                Text(
                    text = ":",
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.padding(horizontal = CinemaSpacing.md)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { minute = (minute + 1) % 60 }) {
                        Icon(androidx.compose.material.icons.filled.KeyboardArrowUp, "Minute Up")
                    }
                    Text(
                        text = "%02d".format(minute),
                        style = MaterialTheme.typography.displayMedium
                    )
                    IconButton(onClick = { minute = if (minute == 0) 59 else minute - 1 }) {
                        Icon(androidx.compose.material.icons.filled.KeyboardArrowDown, "Minute Down")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave("%02d:%02d".format(hour, minute)) }) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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

private fun formatTimestamp(context: android.content.Context, millis: Long): String {
    val dateFormat = android.text.format.DateFormat.getMediumDateFormat(context)
    val timeFormat = android.text.format.DateFormat.getTimeFormat(context)
    val date = java.util.Date(millis)
    return "${dateFormat.format(date)}, ${timeFormat.format(date)}"
}

private fun formatEpochDate(context: android.content.Context, epochSeconds: Long): String {
    return formatTimestamp(context, epochSeconds * 1000L)
}
