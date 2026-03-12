package org.njarasoa.fijerena.feature.epg

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
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileEpgManagementScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: EpgManagementViewModel = viewModel(
        factory = remember { SettingsViewModelFactory(context.applicationContext) }
    )

    val sources by viewModel.sources.collectAsStateWithLifecycle(initialValue = emptyList())
    val staleSourceCount by viewModel.staleSourceCount.collectAsStateWithLifecycle()
    val failedSourceCount by viewModel.failedSourceCount.collectAsStateWithLifecycle()
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()
    val indexState by viewModel.indexState.collectAsStateWithLifecycle()
    val queuedTaskIds by viewModel.queuedTaskIds.collectAsStateWithLifecycle()

    val nowMs = remember { System.currentTimeMillis() }

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<EpgSourceEntity?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

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
                        Icon(Icons.Default.Add, "Add Source")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(CinemaSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md)
            ) {
                // Quick Actions
                if (staleSourceCount > 0 || failedSourceCount > 0) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)
                        ) {
                            if (staleSourceCount > 0) {
                                Button(
                                    onClick = { viewModel.refreshStale() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Refresh Stale ($staleSourceCount)")
                                }
                            }
                            if (failedSourceCount > 0) {
                                Button(
                                    onClick = { viewModel.refreshFailed() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Retry Failed ($failedSourceCount)")
                                }
                            }
                        }
                    }
                }

                // Processing Status
                item {
                    EpgStatusCard(processingState, indexState, queuedTaskIds)
                }

                // Maintenance section
                item {
                    GlassPanel {
                        Column(modifier = Modifier.padding(CinemaSpacing.md)) {
                            Text("Maintenance", style = MaterialTheme.typography.titleMedium, color = CinemaAccentLight)
                            Text(
                                "Manage local database and temporary files.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                            )
                            
                            Spacer(modifier = Modifier.height(CinemaSpacing.md))
                            
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
                    }
                }

                // Source rows
                item {
                    Text("Sources", style = MaterialTheme.typography.titleMedium, color = CinemaAccentLight)
                }

                items(sources, key = { it.id }) { source ->
                    EpgSourceCard(
                        source = source,
                        onRefresh = { viewModel.refreshSource(source.id) },
                        onEdit = { editingSource = source },
                        onDelete = { viewModel.deleteSource(source.id) }
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
            }
        )
    }

    editingSource?.let { source ->
        EpgSourceEditDialog(
            initialSource = source,
            onDismiss = { editingSource = null },
            onConfirm = { url, label, tz, method, enabled ->
                viewModel.updateSource(source.copy(url = url, label = label, timezoneOffsetHours = tz, ingestMethod = method, enabled = enabled))
                editingSource = null
            }
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
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaError)
                ) { Text("Clear Everything") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            },
            title = { Text("Clear EPG Data?") },
            text = { Text("This will delete all indexed programmes and channels. Your source URLs will be preserved.") }
        )
    }
}

@Composable
private fun EpgSourceCard(
    source: EpgSourceEntity,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    GlassPanel {
        Column(modifier = Modifier.padding(CinemaSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = source.label.ifBlank { "Unnamed Source" },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = source.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, "Refresh")
                }
            }

            Spacer(modifier = Modifier.height(CinemaSpacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.md)
            ) {
                val lastIngested = if (source.lastIngestedAtMs > 0) {
                    java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(source.lastIngestedAtMs))
                } else "Never"

                SourceStat("Last Sync", lastIngested)
                SourceStat("Channels", source.lastChannels.toString())
                SourceStat("Programmes", source.lastProgrammes.toString())
            }

            if (source.lastError != null) {
                Text(
                    text = "Error: ${source.lastError}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CinemaError,
                    modifier = Modifier.padding(top = CinemaSpacing.xs)
                )
            }

            Spacer(modifier = Modifier.height(CinemaSpacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onEdit) {
                    Text("Edit")
                }
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = CinemaError)
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
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaError)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
            title = { Text("Delete Source?") },
            text = { Text("Are you sure you want to remove '${source.label}'? All associated EPG data will be deleted.") }
        )
    }
}

@Composable
private fun SourceStat(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun EpgStatusCard(
    multiState: org.njarasoa.fijerena.core.network.xmltv.EpgFileManager.MultiSourceState,
    indexState: EpgIndexState,
    queuedTaskIds: Set<String>
) {
    GlassPanel {
        Column(modifier = Modifier.padding(CinemaSpacing.md)) {
            Text("System Status", style = MaterialTheme.typography.titleMedium, color = CinemaAccentLight)
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))

            // Indexer State
            val indexText = when (indexState) {
                is EpgIndexState.Indexed -> "Database: ${indexState.programmeCount} programmes indexed"
                is EpgIndexState.Indexing -> "Database: Indexing in progress..."
                is EpgIndexState.NotIndexed -> "Database: Empty"
                is EpgIndexState.Failed -> "Database Error: ${indexState.reason}"
            }
            Text(indexText, style = MaterialTheme.typography.bodySmall)

            // Pipeline State
            val pipelineText = when (multiState) {
                is org.njarasoa.fijerena.core.network.xmltv.EpgFileManager.MultiSourceState.Idle -> {
                    val queued = queuedTaskIds.count { it.startsWith("epg_refresh_") }
                    if (queued > 0) "$queued refresh tasks queued" else "Pipeline: Idle"
                }
                is org.njarasoa.fijerena.core.network.xmltv.EpgFileManager.MultiSourceState.Processing -> "Pipeline: Processing ${multiState.completedCount}/${multiState.totalSources} sources"
                is org.njarasoa.fijerena.core.network.xmltv.EpgFileManager.MultiSourceState.Completed -> "Pipeline: Last run completed"
                is org.njarasoa.fijerena.core.network.xmltv.EpgFileManager.MultiSourceState.Finalizing -> "Pipeline: Finalizing..."
                is org.njarasoa.fijerena.core.network.xmltv.EpgFileManager.MultiSourceState.Clearing -> "Pipeline: Clearing data..."
                is org.njarasoa.fijerena.core.network.xmltv.EpgFileManager.MultiSourceState.Error -> "Pipeline Error: ${multiState.reason}"
                else -> "Pipeline: Idle"
            }
            Text(pipelineText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EpgSourceEditDialog(
    initialSource: EpgSourceEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (url: String, label: String, tz: Int, method: String, enabled: Boolean) -> Unit
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
                    onConfirm(url, label, tzOffset.toIntOrNull() ?: 0, initialSource?.ingestMethod ?: "DOWNLOADED", initialSource?.enabled ?: true) 
                },
                enabled = url.isNotBlank()
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
                    singleLine = true
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = tzOffset,
                    onValueChange = { tzOffset = it },
                    label = { Text("Timezone Offset (Hours)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
    )
}
