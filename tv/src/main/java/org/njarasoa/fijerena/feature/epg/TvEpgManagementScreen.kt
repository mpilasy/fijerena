@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.epg

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgSourceEntity
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModelFactory
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.CinemaSurface
import org.njarasoa.fijerena.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.Spacing

@Composable
fun TvEpgManagementScreen(
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

        Spacer(modifier = Modifier.height(Spacing.xl))

        TvLazyColumn(
            contentPadding = PaddingValues(vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.fillMaxSize()
        ) {
            // Status section
            item {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text(
                            text = "Status",
                            style = MaterialTheme.typography.titleMedium,
                            color = CinemaAccent
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))

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
                                is EpgIndexState.Indexed -> CinemaAccent
                                is EpgIndexState.Failed -> CinemaError
                                else -> CinemaTextSecondary
                            }
                        )

                        // Processing state
                        val procState = processingState
                        if (procState is EpgFileManager.MultiSourceState.Processing) {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = "Source ${procState.sourceIndex}/${procState.totalSources}: ${procState.phase} ${procState.sourceLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = CinemaTextSecondary
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
                                        color = CinemaTextSecondary
                                    )
                                }
                                if (procState.completedSourceStats.isNotEmpty()) {
                                    procState.completedSourceStats.forEach { stat ->
                                        Text(
                                            text = "${stat.label}: ${formatBytes(stat.downloadBytes)}, ${formatCount(stat.channelsIngested)}ch, ${formatCount(stat.programmesIngested)}prg" +
                                                (stat.error?.let { " [$it]" } ?: ""),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (stat.error != null) CinemaError else CinemaTextSecondary
                                        )
                                    }
                                }
                            }
                        }
                        if (procState is EpgFileManager.MultiSourceState.Completed) {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = "Completed: ${procState.sourcesProcessed} sources" +
                                    if (procState.errors > 0) " (${procState.errors} errors)" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (procState.errors > 0) CinemaError else CinemaAccent
                            )
                            if (viewModel.isDevMode) {
                                Text(
                                    text = "Total: ${formatBytes(procState.totalDownloadBytes)}, ${formatCount(procState.totalChannels)}ch, ${formatCount(procState.totalProgrammes)}prg",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CinemaTextSecondary
                                )
                                procState.sourceStats.forEach { stat ->
                                    Text(
                                        text = "${stat.label}: ${formatBytes(stat.downloadBytes)}, ${formatCount(stat.channelsIngested)}ch, ${formatCount(stat.programmesIngested)}prg" +
                                            (stat.error?.let { " [$it]" } ?: ""),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (stat.error != null) CinemaError else CinemaTextSecondary
                                    )
                                }
                            }
                        }
                        if (procState is EpgFileManager.MultiSourceState.Error) {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = procState.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = CinemaError
                            )
                        }
                    }
                }
            }

            // Sources section
            item {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sources (${sources.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = CinemaAccent
                            )
                            CinemaPrimaryButton(
                                onClick = { showAddDialog = true },
                                text = "Add Source"
                            )
                        }
                    }
                }
            }

            // Source rows
            items(sources, key = { it.id }) { source ->
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Status dot
                            val dotColor = when {
                                !source.enabled -> CinemaTextSecondary.copy(alpha = CinemaAlpha.textLow)
                                source.lastError != null -> CinemaError
                                source.lastIngestedAtMs > 0 && (System.currentTimeMillis() - source.lastIngestedAtMs) < 24 * 3600 * 1000 -> CinemaAccent
                                source.lastIngestedAtMs > 0 -> androidx.compose.ui.graphics.Color(0xFFFFAB40) // stale yellow
                                else -> CinemaTextSecondary.copy(alpha = CinemaAlpha.textLow)
                            }
                            Surface(
                                modifier = Modifier.size(Spacing.sm),
                                shape = CircleShape,
                                color = dotColor
                            ) {}

                            Spacer(modifier = Modifier.width(Spacing.sm))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = source.label.ifBlank { EpgFileManager.extractLabel(source.url) },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = source.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                    maxLines = 1
                                )
                                val tzLabel = if (source.timezoneOffsetHours == 0) "Auto" else {
                                    val sign = if (source.timezoneOffsetHours >= 0) "+" else ""
                                    "UTC${sign}${source.timezoneOffsetHours}"
                                }
                                val infoLine = buildString {
                                    append("TZ: $tzLabel")
                                    if (source.lastIngestedAtMs > 0) {
                                        append(" | Last: ${formatTimestamp(source.lastIngestedAtMs)}")
                                    }
                                    if (!source.enabled) append(" | DISABLED")
                                }
                                Text(
                                    text = infoLine,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textLow)
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
                                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textLow)
                                    )
                                    var latestTime by remember { mutableStateOf<Long?>(null) }
                                    LaunchedEffect(source.id, source.lastIngestedAtMs) {
                                        latestTime = viewModel.getLatestProgrammeTime(source.id)
                                    }
                                    latestTime?.let { epoch ->
                                        Text(
                                            text = "Latest: ${formatEpochDate(epoch)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textLow)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(Spacing.sm))
                            CinemaPrimaryButton(
                                onClick = { viewModel.refreshSource(source.id) },
                                text = "Refresh"
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            CinemaSecondaryButton(
                                onClick = { editingSource = source },
                                text = "Edit"
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            CinemaDangerButton(
                                onClick = { showDeleteConfirm = source.id },
                                text = "Delete"
                            )
                        }
                    }
                }
            }

            // Actions section
            item {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text(
                            text = "Actions",
                            style = MaterialTheme.typography.titleMedium,
                            color = CinemaAccent
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Auto-refresh (every 24h)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            CinemaSecondaryButton(
                                onClick = {
                                    autoRefreshEnabled = !autoRefreshEnabled
                                    viewModel.setAutoRefreshEnabled(autoRefreshEnabled)
                                },
                                text = if (autoRefreshEnabled) "ON" else "OFF"
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            CinemaPrimaryButton(
                                onClick = { viewModel.refreshAll() },
                                enabled = sources.isNotEmpty(),
                                text = "Refresh All"
                            )
                            CinemaSecondaryButton(
                                onClick = { viewModel.cleanupFiles() },
                                text = "Cleanup Files"
                            )
                            CinemaSecondaryButton(
                                onClick = { viewModel.purgeOldProgrammes() },
                                text = "Purge >7 Days"
                            )
                            CinemaDangerButton(
                                onClick = { showClearConfirm = true },
                                text = "Clear All Data"
                            )
                        }
                    }
                }
            }
        }
    }

    // Add/Edit Source Dialog
    if (showAddDialog || editingSource != null) {
        SourceDialog(
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

    // Clear All confirmation
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = CinemaSurface,
            titleContentColor = CinemaTextPrimary,
            textContentColor = CinemaTextSecondary,
            title = { Text("Clear All EPG Data") },
            text = { Text("This will delete all indexed programmes and channels. Sources will be kept.") },
            confirmButton = {
                CinemaDangerButton(
                    onClick = {
                        viewModel.clearDatabase()
                        showClearConfirm = false
                    },
                    text = "Clear"
                )
            },
            dismissButton = {
                CinemaSecondaryButton(
                    onClick = { showClearConfirm = false },
                    text = "Cancel"
                )
            }
        )
    }

    // Delete source confirmation
    showDeleteConfirm?.let { sourceId ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            containerColor = CinemaSurface,
            titleContentColor = CinemaTextPrimary,
            textContentColor = CinemaTextSecondary,
            title = { Text("Delete Source") },
            text = { Text("Remove this EPG source?") },
            confirmButton = {
                CinemaDangerButton(
                    onClick = {
                        viewModel.deleteSource(sourceId)
                        showDeleteConfirm = null
                    },
                    text = "Delete"
                )
            },
            dismissButton = {
                CinemaSecondaryButton(
                    onClick = { showDeleteConfirm = null },
                    text = "Cancel"
                )
            }
        )
    }
}

@Composable
private fun SourceDialog(
    source: EpgSourceEntity?,
    onDismiss: () -> Unit,
    onSave: (url: String, label: String, tz: Int) -> Unit
) {
    var url by remember { mutableStateOf(source?.url ?: "") }
    var label by remember { mutableStateOf(source?.label ?: "") }
    var tzOffset by remember { mutableIntStateOf(source?.timezoneOffsetHours ?: 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CinemaSurface,
        titleContentColor = CinemaTextPrimary,
        textContentColor = CinemaTextSecondary,
        title = { Text(if (source != null) "Edit Source" else "Add Source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(if (source != null) "XMLTV URL" else "XMLTV URL(s)") },
                    placeholder = { Text(if (source != null) "https://epg.example.com/guide.xml.gz" else "One or more URLs (one per line)") },
                    singleLine = source != null,
                    maxLines = if (source != null) 1 else 5,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CinemaTextPrimary,
                        unfocusedTextColor = CinemaTextPrimary,
                        cursorColor = CinemaAccent,
                        focusedBorderColor = CinemaAccent,
                        unfocusedBorderColor = CinemaTextSecondary,
                        focusedLabelColor = CinemaAccent,
                        unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        focusedContainerColor = CinemaSurfaceVariant,
                        focusedPlaceholderColor = CinemaTextSecondary,
                        unfocusedPlaceholderColor = CinemaTextSecondary
                    )
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (optional)") },
                    placeholder = { Text("Auto-detected from URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CinemaTextPrimary,
                        unfocusedTextColor = CinemaTextPrimary,
                        cursorColor = CinemaAccent,
                        focusedBorderColor = CinemaAccent,
                        unfocusedBorderColor = CinemaTextSecondary,
                        focusedLabelColor = CinemaAccent,
                        unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        focusedContainerColor = CinemaSurfaceVariant,
                        focusedPlaceholderColor = CinemaTextSecondary,
                        unfocusedPlaceholderColor = CinemaTextSecondary
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Timezone:",
                        style = MaterialTheme.typography.bodySmall,
                        color = CinemaTextSecondary
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    val tzLabel = if (tzOffset == 0) "Auto (from data)" else {
                        val sign = if (tzOffset >= 0) "+" else ""
                        "UTC${sign}${tzOffset}"
                    }
                    CinemaSecondaryButton(
                        onClick = {
                            tzOffset = (tzOffset + 1).let { if (it > 14) -12 else it }
                        },
                        text = tzLabel
                    )
                }
            }
        },
        confirmButton = {
            CinemaPrimaryButton(
                onClick = { onSave(url, label, tzOffset) },
                enabled = url.isNotBlank(),
                text = "Save"
            )
        },
        dismissButton = {
            CinemaSecondaryButton(
                onClick = onDismiss,
                text = "Cancel"
            )
        }
    )
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
        count >= 1_000 -> "%.1fK".format(count / 1_000.0)
        else -> count.toString()
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
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
