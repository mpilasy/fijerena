@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)

package org.njarasoa.fijerena.feature.epg

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.material.icons.filled.*
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
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
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

    val sources by viewModel.sources.collectAsStateWithLifecycle(initialValue = emptyList())
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()
    val indexState by viewModel.indexState.collectAsStateWithLifecycle()
    val dbStats by viewModel.dbStats.collectAsStateWithLifecycle()
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

    val appSettings = remember { org.njarasoa.fijerena.core.network.AppSettings(context.applicationContext) }
    val uiScale by remember { mutableStateOf(appSettings.uiScale) }
    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100) // Give it a moment to attach
        try {
            initialFocusRequester.requestFocus()
        } catch (_: IllegalStateException) {
            // FocusRequester not yet attached — ignore
        }
    }

    CompositionLocalProvider(LocalUiScale provides uiScale) {
    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    val scaledDisplaySmall = remember(scale) { typography.displaySmall.copy(fontSize = typography.displaySmall.fontSize.scaled(scale)) }
    val scaledTitleLarge = remember(scale) { typography.titleLarge.copy(fontSize = typography.titleLarge.fontSize.scaled(scale)) }
    val scaledTitleMedium = remember(scale) { typography.titleMedium.copy(fontSize = typography.titleMedium.fontSize.scaled(scale)) }
    val scaledBodyLarge = remember(scale) { typography.bodyLarge.copy(fontSize = typography.bodyLarge.fontSize.scaled(scale)) }
    val scaledBodyMedium = remember(scale) { typography.bodyMedium.copy(fontSize = typography.bodyMedium.fontSize.scaled(scale)) }
    val scaledBodySmall = remember(scale) { typography.bodySmall.copy(fontSize = typography.bodySmall.fontSize.scaled(scale)) }
    val scaledLabelSmall = remember(scale) { typography.labelSmall.copy(fontSize = typography.labelSmall.fontSize.scaled(scale)) }
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
            style = scaledDisplaySmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))

        TvLazyColumn(
            contentPadding = PaddingValues(vertical = Spacing.xs.scaled(scale)),
            verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Status section
            item(contentType = "status_section") {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
                        Text(
                            text = "Status",
                            style = scaledTitleMedium,
                            color = CinemaAccent
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

                        val statusText = when (val state = indexState) {
                            is EpgIndexState.NotIndexed -> "No EPG data"
                            is EpgIndexState.Indexing -> "Indexing: ${state.progressPercent}%"
                            is EpgIndexState.Indexed -> "${NumberUtils.formatCount(state.channelCount)} channels, ${NumberUtils.formatCount(state.programmeCount)} programmes"
                            is EpgIndexState.Failed -> "Failed: ${state.reason}"
                        }
                        Text(
                            text = statusText,
                            style = scaledBodyMedium,
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
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${procState.completedCount}/${procState.totalSources} sources",
                                    style = scaledBodySmall,
                                    color = CinemaTextSecondary,
                                    modifier = Modifier.weight(1f)
                                )
                                androidx.compose.material3.TextButton(onClick = { viewModel.cancelProcessing() }) {
                                    Text("Cancel", style = scaledLabelSmall, color = CinemaError)
                                }
                            }
                            if (procState.totalChannels > 0 || procState.totalProgrammes > 0) {
                                Text(
                                    text = "Total: ${NumberUtils.formatCount(procState.totalChannels)}ch, ${NumberUtils.formatCount(procState.totalProgrammes)}prg",
                                    style = scaledLabelSmall,
                                    color = CinemaTextSecondary
                                )
                            }
                        }
                        if (procState is EpgFileManager.MultiSourceState.Completed) {
                            Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                            Text(
                                text = "Completed: ${procState.sourcesProcessed} sources" +
                                    if (procState.errors > 0) " (${procState.errors} errors)" else "",
                                style = scaledBodySmall,
                                color = if (procState.errors > 0) CinemaError else CinemaAccent
                            )
                            if (viewModel.isDevMode) {
                                Text(
                                    text = "Last update: ${NumberUtils.formatTimestamp(context, procState.updatedAtMs)} (took ${NumberUtils.formatDuration(procState.durationMs)})",
                                    style = scaledLabelSmall,
                                    color = CinemaTextSecondary
                                )
                                Text(
                                    text = "Total: ${NumberUtils.formatBytes(procState.totalDownloadBytes)}, ${NumberUtils.formatCount(procState.totalChannels)}ch, ${NumberUtils.formatCount(procState.totalProgrammes)}prg",
                                    style = scaledLabelSmall,
                                    color = CinemaTextSecondary
                                )
                            }
                        }
                        if (procState is EpgFileManager.MultiSourceState.Error) {
                            Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                            Text(
                                text = procState.reason,
                                style = scaledBodySmall,
                                color = CinemaError
                            )
                        }
                    }
                }
            }

            // Sources section
            item(contentType = "sources_header") {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sources (${sources.size})",
                                style = scaledTitleMedium,
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
            items(sources, key = { it.id }, contentType = { "source" }) { source ->
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Selection checkbox
                            val isSelected = source.id in selectedSourceIds
                            CinemaIconButton(
                                onClick = {
                                    selectedSourceIds = if (isSelected) selectedSourceIds - source.id
                                    else selectedSourceIds + source.id
                                },
                                icon = {
                                    Icon(
                                        if (isSelected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                        contentDescription = "Select",
                                        tint = if (isSelected) CinemaAccent else CinemaTextSecondary
                                    )
                                }
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs.scaled(scale)))

                            // Status dot
                            val dotColor = when {
                                !source.enabled -> CinemaTextSecondary.copy(alpha = CinemaAlpha.textLow)
                                source.lastError != null -> CinemaError
                                source.lastIngestedAtMs > 0 && (nowMs - source.lastIngestedAtMs) < 24 * 3600 * 1000 -> CinemaAccent
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
                                    style = scaledBodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = source.url,
                                    style = scaledBodySmall,
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
                                        append(" | Last: ${NumberUtils.formatTimestamp(context, source.lastIngestedAtMs)}")
                                    }
                                    if (!source.enabled) append(" | DISABLED")
                                }
                                Text(
                                    text = infoLine,
                                    style = scaledLabelSmall,
                                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textLow)
                                )
                                source.lastError?.let { error ->
                                    Text(
                                        text = error,
                                        style = scaledLabelSmall,
                                        color = CinemaError,
                                        maxLines = 1
                                    )
                                }

                                // In-progress or completed stats from current processing
                                val procState = processingState
                                if (procState is EpgFileManager.MultiSourceState.Processing) {
                                    val progress = procState.activeProgress[source.id]
                                    val completedStat = procState.completedSourceStats[source.id]

                                    if (progress != null) {
                                        val progressText = buildString {
                                            append(progress.phase)
                                            if (progress.progressPercent in 0..100) {
                                                append(" ${progress.progressPercent}%")
                                            }
                                            if (progress.phase == "Downloading") {
                                                append(" (${NumberUtils.formatBytes(progress.downloadedBytes)}")
                                                if (progress.downloadTotalBytes > 0) {
                                                    append("/${NumberUtils.formatBytes(progress.downloadTotalBytes)}")
                                                }
                                                append(")")
                                            } else if (progress.phase == "Awaiting Ingestion") {
                                                append(" (${NumberUtils.formatBytes(progress.downloadedBytes)})")
                                            } else if (progress.programmes > 0) {
                                                append(" (${NumberUtils.formatCount(progress.channels)}ch, ${NumberUtils.formatCount(progress.programmes)}prg)")
                                            }
                                        }
                                        Text(
                                            text = progressText,
                                            style = scaledLabelSmall,
                                            color = CinemaAccent
                                        )
                                    } else if (completedStat != null) {
                                        val statText = buildString {
                                            append("Completed")
                                            append(" (${NumberUtils.formatBytes(completedStat.downloadBytes)}, ${NumberUtils.formatCount(completedStat.channelsIngested)}ch, ${NumberUtils.formatCount(completedStat.programmesIngested)}prg)")
                                            if (completedStat.durationMs > 0) {
                                                append(" in ${NumberUtils.formatDuration(completedStat.durationMs)}")
                                            }
                                            if (completedStat.error != null) append(" [${completedStat.error}]")
                                        }
                                        Text(
                                            text = statText,
                                            style = scaledLabelSmall,
                                            color = if (completedStat.error != null) CinemaError else CinemaAccent
                                        )
                                    }
                                } else if (procState is EpgFileManager.MultiSourceState.Completed) {
                                    val stat = procState.sourceStats[source.id]
                                    if (stat != null) {
                                        val statText = buildString {
                                            append("Finished")
                                            append(" (${NumberUtils.formatBytes(stat.downloadBytes)}, ${NumberUtils.formatCount(stat.channelsIngested)}ch, ${NumberUtils.formatCount(stat.programmesIngested)}prg)")
                                            if (stat.durationMs > 0) {
                                                append(" in ${NumberUtils.formatDuration(stat.durationMs)}")
                                            }
                                            if (stat.error != null) append(" [${stat.error}]")
                                        }
                                        Text(
                                            text = statText,
                                            style = scaledLabelSmall,
                                            color = if (stat.error != null) CinemaError else CinemaAccent
                                        )
                                    }
                                }

                                if (viewModel.isDevMode && source.lastIngestedAtMs > 0) {
                                    val sizeStr = if (source.ingestMethod != "STREAMED") {
                                        ", ${NumberUtils.formatBytes(source.lastDownloadBytes)}"
                                    } else {
                                        ""
                                    }
                                    Text(
                                        text = "${NumberUtils.formatCount(source.lastChannels)}ch, ${NumberUtils.formatCount(source.lastProgrammes)}prg$sizeStr",
                                        style = scaledLabelSmall,
                                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textLow)
                                    )
                                    var latestTime by remember { mutableStateOf<Long?>(null) }
                                    LaunchedEffect(source.id, source.lastIngestedAtMs) {
                                        latestTime = viewModel.getLatestProgrammeTime(source.id)
                                    }
                                    latestTime?.let { epoch ->
                                        Text(
                                            text = "Latest: ${NumberUtils.formatEpochDate(context, epoch)}",
                                            style = scaledLabelSmall,
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
            item(contentType = "actions_section") {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
                        Text(
                            text = "Actions",
                            style = scaledTitleMedium,
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
                                style = scaledBodyMedium,
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Refresh Time",
                                style = scaledBodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            CinemaSecondaryButton(
                                onClick = { showTimeDialog = true },
                                text = epgRefreshTime
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
                            if (selectedSourceIds.isNotEmpty()) {
                                CinemaPrimaryButton(
                                    onClick = {
                                        viewModel.refreshSelected(selectedSourceIds)
                                        selectedSourceIds = emptySet()
                                    },
                                    text = "Selected (${selectedSourceIds.size})"
                                )
                            }
                            CinemaSecondaryButton(
                                onClick = { showCleanupConfirm = true },
                                enabled = hasStrayFiles,
                                text = "Cleanup Files"
                            )
                            CinemaSecondaryButton(
                                onClick = { showPurgeConfirm = true },
                                enabled = staleProgrammeCount > 0,
                                text = "Purge >2 Days"
                            )
                            CinemaDangerButton(
                                onClick = { showClearConfirm = true },
                                text = "Clear All Data"
                            )
                        }
                        if (viewModel.isDevMode) {
                            val hasFailed = sources.any { it.enabled && it.lastError != null }
                            val hasOutdated = sources.any { it.enabled && (it.lastIngestedAtMs == 0L || (nowMs - it.lastIngestedAtMs) > 24 * 3600 * 1000) }
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

    if (showTimeDialog) {
        TimeDialog(
            currentTime = epgRefreshTime,
            onDismiss = { showTimeDialog = false },
            onSave = { newTime ->
                epgRefreshTime = newTime
                viewModel.setEpgRefreshTime(newTime)
                showTimeDialog = false
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
                    style = scaledTitleLarge
                )
            },
            text = {
                Text(
                    text = "This will delete any downloaded EPG files that are no longer associated with a source. The indexed data in the database is not affected.",
                    style = scaledBodyMedium
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
                    style = scaledTitleLarge
                )
            },
            text = {
                Text(
                    text = "This will permanently delete all programme data older than 2 days from the database. Channel entries are not affected.",
                    style = scaledBodyMedium
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
                    style = scaledTitleLarge
                )
            },
            text = {
                Text(
                    text = "This will delete all indexed programmes and channels. Sources will be kept.",
                    style = scaledBodyMedium
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
                    style = scaledTitleLarge
                )
            },
            text = {
                Text(
                    text = "Remove this EPG source?",
                    style = scaledBodyMedium
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

    // Blocking overlay while clearing data
    if (processingState is EpgFileManager.MultiSourceState.Clearing) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            // Semi-transparent background to block interaction
            Box(modifier = Modifier
                .fillMaxSize()
                .background(CinemaSurface.copy(alpha = 0.85f))
            )
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = CinemaAccent,
                    modifier = Modifier.size(Spacing.xxl.scaled(scale))
                )
                Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
                Text(
                    text = "Clearing all EPG data...",
                    style = scaledTitleMedium,
                    color = CinemaTextPrimary
                )
                Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                Text(
                    text = "This may take a moment",
                    style = scaledBodySmall,
                    color = CinemaTextSecondary
                )
            }
        }
    }

    } // End CompositionLocalProvider
}

@Composable
private fun TimeDialog(
    currentTime: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val initialHour = currentTime.substringBefore(":").toIntOrNull() ?: 2
    val initialMinute = currentTime.substringAfter(":").toIntOrNull() ?: 0
    var hour by remember { mutableIntStateOf(initialHour) }
    var minute by remember { mutableIntStateOf(initialMinute) }
    val scale = LocalUiScale.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CinemaSurface,
        titleContentColor = CinemaTextPrimary,
        textContentColor = CinemaTextSecondary,
        title = {
            Text(
                text = "EPG Refresh Time",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize.scaled(scale))
            )
        },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CinemaIconButton(
                        onClick = { hour = (hour + 1) % 24 },
                        icon = { Icon(Icons.Default.KeyboardArrowUp, "Hour Up") }
                    )
                    Text(
                        text = "%02d".format(hour),
                        style = MaterialTheme.typography.displayMedium.copy(fontSize = MaterialTheme.typography.displayMedium.fontSize.scaled(scale)),
                        color = CinemaTextPrimary
                    )
                    CinemaIconButton(
                        onClick = { hour = if (hour == 0) 23 else hour - 1 },
                        icon = { Icon(Icons.Default.KeyboardArrowDown, "Hour Down") }
                    )
                }
                Text(
                    text = ":",
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = MaterialTheme.typography.displayMedium.fontSize.scaled(scale)),
                    color = CinemaTextPrimary,
                    modifier = Modifier.padding(horizontal = Spacing.md.scaled(scale))
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CinemaIconButton(
                        onClick = { minute = (minute + 1) % 60 },
                        icon = { Icon(Icons.Default.KeyboardArrowUp, "Minute Up") }
                    )
                    Text(
                        text = "%02d".format(minute),
                        style = MaterialTheme.typography.displayMedium.copy(fontSize = MaterialTheme.typography.displayMedium.fontSize.scaled(scale)),
                        color = CinemaTextPrimary
                    )
                    CinemaIconButton(
                        onClick = { minute = if (minute == 0) 59 else minute - 1 },
                        icon = { Icon(Icons.Default.KeyboardArrowDown, "Minute Down") }
                    )
                }
            }
        },
        confirmButton = {
            CinemaPrimaryButton(
                onClick = { onSave("%02d:%02d".format(hour, minute)) },
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
                style = MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize.scaled(scale))
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
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)),
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
