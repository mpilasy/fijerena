package org.njarasoa.fijerena.feature.epg

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.buttons.CinemaDangerButton
import org.njarasoa.fijerena.core.ui.components.buttons.CinemaDangerIconButton
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.dialogs.CinemaAlertDialog
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun TvEpgManagementScreen(
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

    val scale = LocalUiScale.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = Spacing.tvSafeMarginHorizontal,
                    vertical = Spacing.tvSafeMarginVertical
                )
        ) {
            Text(
                text = "EPG Management",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))

            TvLazyColumn(
                contentPadding = PaddingValues(vertical = Spacing.xs.scaled(scale)),
                verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                modifier = Modifier.fillMaxSize()
            ) {
                // Header Actions
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale))
                    ) {
                        CinemaPrimaryButton(
                            onClick = { showAddDialog = true },
                            text = "Add Source",
                            leadingIcon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp)) }
                        )

                        if (staleSourceCount > 0) {
                            CinemaSecondaryButton(
                                onClick = { viewModel.refreshStale() },
                                text = "Refresh Stale ($staleSourceCount)"
                            )
                        }

                        if (failedSourceCount > 0) {
                            CinemaSecondaryButton(
                                onClick = { viewModel.refreshFailed() },
                                text = "Retry Failed ($failedSourceCount)"
                            )
                        }
                    }
                }

                // Processing Status
                item {
                    EpgStatusCard(processingState, indexState, queuedTaskIds, scale)
                }

                // Maintenance Actions
                item {
                    GlassPanel {
                        Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
                            Text("Maintenance", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                            CinemaDangerButton(
                                onClick = { showClearConfirm = true },
                                text = "Clear All Data"
                            )
                        }
                    }
                }

                // Source rows
                items(sources, key = { it.id }, contentType = { "source" }) { source ->
                    GlassPanel(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
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

                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
                                    CinemaSecondaryButton(
                                        onClick = { viewModel.refreshSource(source.id) },
                                        text = "Refresh"
                                    )
                                    CinemaSecondaryButton(
                                        onClick = { editingSource = source },
                                        text = "Edit"
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

                            // Source Stats Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.lg.scaled(scale))
                            ) {
                                val lastIngested = if (source.lastIngestedAtMs > 0) {
                                    java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                                        .format(java.util.Date(source.lastIngestedAtMs))
                                } else "Never"

                                SourceStat("Last Sync", lastIngested, scale)
                                SourceStat("Channels", source.lastChannels.toString(), scale)
                                SourceStat("Programmes", source.lastProgrammes.toString(), scale)
                                
                                if (source.lastError != null) {
                                    Text(
                                        text = "Error: ${source.lastError}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
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
            scale = scale
        )
    }

    editingSource?.let { source ->
        EpgSourceEditDialog(
            initialSource = source,
            onDismiss = { editingSource = null },
            onConfirm = { url, label, tz, method, enabled ->
                viewModel.updateSource(source.copy(url = url, label = label, timezoneOffsetHours = tz, ingestMethod = method, enabled = enabled))
                editingSource = null
            },
            scale = scale
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
                    text = "Clear Everything"
                )
            },
            dismissButton = {
                CinemaSecondaryButton(
                    onClick = { showClearConfirm = false },
                    text = "Cancel"
                )
            },
            title = "Clear EPG Data?",
            text = "This will delete all indexed programmes and channels. Your source URLs will be preserved."
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SourceStat(label: String, value: String, scale: Float) {
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpgStatusCard(
    multiState: org.njarasoa.fijerena.core.network.xmltv.EpgFileManager.MultiSourceState,
    indexState: EpgIndexState,
    queuedTaskIds: Set<String>,
    scale: Float
) {
    GlassPanel {
        Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
            Text("System Status", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpgSourceEditDialog(
    initialSource: EpgSourceEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (url: String, label: String, tz: Int, method: String, enabled: Boolean) -> Unit,
    scale: Float
) {
    var url by remember { mutableStateOf(initialSource?.url ?: "") }
    var label by remember { mutableStateOf(initialSource?.label ?: "") }
    var tzOffset by remember { mutableStateOf(initialSource?.timezoneOffsetHours?.toString() ?: "0") }
    
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = if (initialSource == null) "Add EPG Source" else "Edit EPG Source",
        confirmButton = {
            CinemaPrimaryButton(
                onClick = { 
                    onConfirm(url, label, tzOffset.toIntOrNull() ?: 0, initialSource?.ingestMethod ?: "DOWNLOADED", initialSource?.enabled ?: true) 
                },
                text = "Save",
                enabled = url.isNotBlank()
            )
        },
        dismissButton = {
            CinemaSecondaryButton(onClick = onDismiss, text = "Cancel")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
                androidx.compose.material3.OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("XMLTV URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                androidx.compose.material3.OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                androidx.compose.material3.OutlinedTextField(
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
