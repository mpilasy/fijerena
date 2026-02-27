@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

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
    val hasStrayFiles by viewModel.hasStrayFiles.collectAsState()
    val staleProgrammeCount by viewModel.staleProgrammeCount.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<EpgSourceEntity?>(null) }
    var autoRefreshEnabled by remember { mutableStateOf(viewModel.autoRefreshEnabled) }
    var showCleanupConfirm by remember { mutableStateOf(false) }
    var showPurgeConfirm by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<Long?>(null) }

    val appSettings = remember { org.njarasoa.fijerena.core.network.AppSettings(context.applicationContext) }
    val uiScale by remember { mutableStateOf(appSettings.uiScale) }
    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        initialFocusRequester.requestFocus()
    }

    CompositionLocalProvider(LocalUiScale provides uiScale) {
    val scale = LocalUiScale.current
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
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = MaterialTheme.typography.displaySmall.fontSize.scaled(scale)
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))

        TvLazyColumn(
            contentPadding = PaddingValues(vertical = Spacing.xs.scaled(scale)),
            verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
            modifier = Modifier
                .fillMaxSize()
                .focusRestorer { initialFocusRequester }
        ) {
            // Status section
            item {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
                        Text(
                            text = "Status",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                            ),
                            color = CinemaAccent
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

                        val statusText = when (val state = indexState) {
                            is EpgIndexState.NotIndexed -> "No EPG data"
                            is EpgIndexState.Indexing -> "Indexing: ${state.progressPercent}%"
                            is EpgIndexState.Indexed -> "${formatCount(state.channelCount)} channels, ${formatCount(state.programmeCount)} programmes"
                            is EpgIndexState.Failed -> "Failed: ${state.reason}"
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)
                            ),
                            color = when (indexState) {
                                is EpgIndexState.Indexed -> CinemaAccent
                                is EpgIndexState.Failed -> CinemaError
                                else -> CinemaTextSecondary
                            }
                        )

                        // Processing state
                        val procState = processingState
                        if (procState is EpgFileManager.MultiSourceState.Processing) {
                            Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                            Text(
                                text = "Source ${procState.sourceIndex}/${procState.totalSources}: ${procState.phase} ${procState.sourceLabel}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                                ),
                                color = CinemaTextSecondary
                            )
                            if (procState.phase == "Streaming" && (procState.sourceChannels > 0 || procState.sourceProgrammes > 0)) {
                                Text(
                                    text = "${formatCount(procState.sourceChannels)}ch, ${formatCount(procState.sourceProgrammes)}prg",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = MaterialTheme.typography.labelSmall.fontSize.scaled(scale)
                                    ),
                                    color = CinemaTextSecondary
                                )
                            }
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
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = MaterialTheme.typography.labelSmall.fontSize.scaled(scale)
                                        ),
                                        color = CinemaTextSecondary
                                    )
                                }
                                if (procState.completedSourceStats.isNotEmpty()) {
                                    procState.completedSourceStats.forEach { stat ->
                                        Text(
                                            text = "${stat.label}: ${formatBytes(stat.downloadBytes)}, ${formatCount(stat.channelsIngested)}ch, ${formatCount(stat.programmesIngested)}prg" +
                                                (stat.error?.let { " [$it]" } ?: ""),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = MaterialTheme.typography.labelSmall.fontSize.scaled(scale)
                                            ),
                                            color = if (stat.error != null) CinemaError else CinemaTextSecondary
                                        )
                                    }
                                }
                            }
                        }
                        if (procState is EpgFileManager.MultiSourceState.Completed) {
                            Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                            Text(
                                text = "Completed: ${procState.sourcesProcessed} sources" +
                                    if (procState.errors > 0) " (${procState.errors} errors)" else "",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                                ),
                                color = if (procState.errors > 0) CinemaError else CinemaAccent
                            )
                            if (viewModel.isDevMode) {
                                Text(
                                    text = "Total: ${formatBytes(procState.totalDownloadBytes)}, ${formatCount(procState.totalChannels)}ch, ${formatCount(procState.totalProgrammes)}prg",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = MaterialTheme.typography.labelSmall.fontSize.scaled(scale)
                                    ),
                                    color = CinemaTextSecondary
                                )
                                procState.sourceStats.forEach { stat ->
                                    Text(
                                        text = "${stat.label}: ${formatBytes(stat.downloadBytes)}, ${formatCount(stat.channelsIngested)}ch, ${formatCount(stat.programmesIngested)}prg" +
                                            (stat.error?.let { " [$it]" } ?: ""),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = MaterialTheme.typography.labelSmall.fontSize.scaled(scale)
                                        ),
                                        color = if (stat.error != null) CinemaError else CinemaTextSecondary
                                    )
                                }
                            }
                        }
                        if (procState is EpgFileManager.MultiSourceState.Error) {
                            Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                            Text(
                                text = procState.reason,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                                ),
                                color = CinemaError
                            )
                        }
                    }
                }
            }

            // Sources section
            item {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sources (${sources.size})",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                                ),
                                color = CinemaAccent
                            )
                            CinemaPrimaryButton(
                                onClick = { showAddDialog = true },
                                text = "Add Source",
                                modifier = Modifier.focusRequester(initialFocusRequester)
                            )
                        }
                    }
                }
            }

            // Source rows
            items(sources, key = { it.id }) { source ->
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
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
                                modifier = Modifier.size(Spacing.sm.scaled(scale)),
                                shape = CircleShape,
                                color = dotColor
                            ) {}

                            Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = source.label.ifBlank { EpgFileManager.extractLabel(source.url) },
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = MaterialTheme.typography.bodyLarge.fontSize.scaled(scale)
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = source.url,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                                    ),
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
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = MaterialTheme.typography.labelSmall.fontSize.scaled(scale)
                                    ),
                                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textLow)
                                )
                                source.lastError?.let { error ->
                                    Text(
                                        text = error,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = MaterialTheme.typography.labelSmall.fontSize.scaled(scale)
                                        ),
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
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = MaterialTheme.typography.labelSmall.fontSize.scaled(scale)
                                        ),
                                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textLow)
                                    )
                                    var latestTime by remember { mutableStateOf<Long?>(null) }
                                    LaunchedEffect(source.id, source.lastIngestedAtMs) {
                                        latestTime = viewModel.getLatestProgrammeTime(source.id)
                                    }
                                    latestTime?.let { epoch ->
                                        Text(
                                            text = "Latest: ${formatEpochDate(epoch)}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = MaterialTheme.typography.labelSmall.fontSize.scaled(scale)
                                            ),
                                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textLow)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))
                            CinemaIconButton(
                                onClick = { viewModel.refreshSource(source.id) },
                                icon = { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs.scaled(scale)))
                            CinemaIconButton(
                                onClick = { editingSource = source },
                                icon = { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs.scaled(scale)))
                            CinemaDangerIconButton(
                                onClick = { showDeleteConfirm = source.id },
                                icon = { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                            )
                        }
                    }
                }
            }

            // Actions section
            item {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
                        Text(
                            text = "Actions",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                            ),
                            color = CinemaAccent
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Auto-refresh (every 24h)",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)
                                ),
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
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))
                        ) {
                            CinemaPrimaryButton(
                                onClick = { viewModel.refreshAll() },
                                enabled = sources.isNotEmpty(),
                                text = "Refresh All"
                            )
                            if (hasStrayFiles) {
                                CinemaSecondaryButton(
                                    onClick = { showCleanupConfirm = true },
                                    text = "Cleanup Files"
                                )
                            }
                            if (staleProgrammeCount > 0) {
                                CinemaSecondaryButton(
                                    onClick = { showPurgeConfirm = true },
                                    text = "Purge >7 Days"
                                )
                            }
                            CinemaDangerButton(
                                onClick = { showClearConfirm = true },
                                text = "Clear All Data"
                            )
                        }
                        if (viewModel.isDevMode) {
                            val hasFailed = sources.any { it.enabled && it.lastError != null }
                            val hasOutdated = sources.any { it.enabled && (it.lastIngestedAtMs == 0L || (System.currentTimeMillis() - it.lastIngestedAtMs) > 24 * 3600 * 1000) }
                            if (hasFailed || hasOutdated) {
                                Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))
                                ) {
                                    if (hasFailed) {
                                        CinemaSecondaryButton(
                                            onClick = { viewModel.refreshFailed() },
                                            text = "Refresh Failed"
                                        )
                                    }
                                    if (hasOutdated) {
                                        CinemaSecondaryButton(
                                            onClick = { viewModel.refreshOutdated() },
                                            text = "Refresh Outdated"
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

    // Cleanup confirmation
    if (showCleanupConfirm) {
        AlertDialog(
            onDismissRequest = { showCleanupConfirm = false },
            containerColor = CinemaSurface,
            titleContentColor = CinemaTextPrimary,
            textContentColor = CinemaTextSecondary,
            title = {
                Text(
                    text = "Cleanup Files",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = MaterialTheme.typography.titleLarge.fontSize.scaled(scale)
                    )
                )
            },
            text = {
                Text(
                    text = "This will delete any downloaded EPG files that are no longer associated with a source. The indexed data in the database is not affected.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)
                    )
                )
            },
            confirmButton = {
                CinemaPrimaryButton(
                    onClick = {
                        viewModel.cleanupFiles()
                        showCleanupConfirm = false
                    },
                    text = "Cleanup"
                )
            },
            dismissButton = {
                CinemaSecondaryButton(
                    onClick = { showCleanupConfirm = false },
                    text = "Cancel"
                )
            }
        )
    }

    // Purge confirmation
    if (showPurgeConfirm) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirm = false },
            containerColor = CinemaSurface,
            titleContentColor = CinemaTextPrimary,
            textContentColor = CinemaTextSecondary,
            title = {
                Text(
                    text = "Purge Old Programmes",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = MaterialTheme.typography.titleLarge.fontSize.scaled(scale)
                    )
                )
            },
            text = {
                Text(
                    text = "This will permanently delete all programme data older than 7 days from the database. Channel entries are not affected.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)
                    )
                )
            },
            confirmButton = {
                CinemaDangerButton(
                    onClick = {
                        viewModel.purgeOldProgrammes()
                        showPurgeConfirm = false
                    },
                    text = "Purge"
                )
            },
            dismissButton = {
                CinemaSecondaryButton(
                    onClick = { showPurgeConfirm = false },
                    text = "Cancel"
                )
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
            title = {
                Text(
                    text = "Clear All EPG Data",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = MaterialTheme.typography.titleLarge.fontSize.scaled(scale)
                    )
                )
            },
            text = {
                Text(
                    text = "This will delete all indexed programmes and channels. Sources will be kept.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)
                    )
                )
            },
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
            title = {
                Text(
                    text = "Delete Source",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = MaterialTheme.typography.titleLarge.fontSize.scaled(scale)
                    )
                )
            },
            text = {
                Text(
                    text = "Remove this EPG source?",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)
                    )
                )
            },
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

    } // End CompositionLocalProvider
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
    val scale = LocalUiScale.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CinemaSurface,
        titleContentColor = CinemaTextPrimary,
        textContentColor = CinemaTextSecondary,
        title = {
            Text(
                text = if (source != null) "Edit Source" else "Add Source",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = MaterialTheme.typography.titleLarge.fontSize.scaled(scale)
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
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
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                        ),
                        color = CinemaTextSecondary
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))
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
