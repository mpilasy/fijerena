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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager.MultiSourceState
import org.njarasoa.fijerena.core.network.xtream.manager.AutoXmltvSources
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDialogActionButton
import org.njarasoa.fijerena.core.ui.components.CinemaDialogTextButton
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.theme.*
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaTextButton

/**
 * One source's guide sources (A-9, M5): the list first, its bulk actions, an empty state that
 * offers to add one. Guide auto-refresh and maintenance are device-wide and live in Settings.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MobileEpgManagementScreen(
    providerId: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: EpgManagementViewModel =
        viewModel(
            factory =
                remember(providerId) {
                    SettingsViewModelFactory(context.applicationContext, providerId = providerId)
                },
        )

    // null until the first emission, so the empty state doesn't flash while the list loads.
    val sources by viewModel.sources.collectAsStateWithLifecycle(initialValue = null)
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val latestProgrammeTimes by viewModel.latestProgrammeTimes.collectAsStateWithLifecycle()
    val staleSourceCount by viewModel.staleSourceCount.collectAsStateWithLifecycle()
    val failedSourceCount by viewModel.failedSourceCount.collectAsStateWithLifecycle()
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()
    val provider by produceState<ProviderEntity?>(initialValue = null, providerId) {
        value =
            AppContainer
                .getInstance(context.applicationContext)
                .providerRepository
                .getProviderById(providerId)
    }
    val providerName = provider?.name

    val nowMs = remember { System.currentTimeMillis() }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.toastMessage.collect { message ->
                android.widget.Toast
                    .makeText(context, message.asString(context), android.widget.Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<EpgSourceEntity?>(null) }
    var deleteSelectedIds by remember { mutableStateOf<Set<Long>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        providerName?.let { stringResource(R.string.epg_management_title_format, it) }
                            ?: stringResource(R.string.epg_sources_header),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
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
                // Bulk actions over this list
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

                if (sources?.isEmpty() == true) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = CinemaSpacing.lg),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
                        ) {
                            Text(
                                stringResource(R.string.epg_summary_no_sources),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
                            )
                            CinemaButton(onClick = { showAddDialog = true }) {
                                Icon(CinemaIcons.Add, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                                Text(stringResource(R.string.epg_add_source))
                            }
                        }
                    }
                }

                items(sources.orEmpty(), key = { it.id }) { source ->
                    val isSelected = selectedIds.contains(source.id)
                    val latestTime = latestProgrammeTimes[source.id] ?: 0L

                    val activeProgress =
                        if (processingState is MultiSourceState.Processing) {
                            (processingState as MultiSourceState.Processing).activeProgress[source.id]
                        } else {
                            null
                        }

                    // Whether the run that just finished confirmed this source's data hadn't
                    // changed (304, or a matching content hash) — true only right after that run.
                    val wasUnchanged =
                        (processingState as? MultiSourceState.Completed)?.sourceStats?.get(source.id)?.unchanged == true

                    EpgSourceCard(
                        source = source,
                        isSelected = isSelected,
                        latestProgrammeTime = latestTime,
                        activeProgress = activeProgress,
                        wasUnchanged = wasUnchanged,
                        isOwnGuideOff = !source.enabled && provider?.let { AutoXmltvSources.isAutoXmltvSource(source, it.url) } == true,
                        nowMs = nowMs,
                        staleThresholdMs = viewModel.staleThresholdMs(source),
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
}

@Composable
internal fun localizedEpgPhase(phase: String): String =
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
    wasUnchanged: Boolean,
    /** The source's own guide, disabled by "Provides a guide" (Edit Source). */
    isOwnGuideOff: Boolean,
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
                            text = guideSourceUrlHint(source.url),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isOwnGuideOff) {
                            Text(
                                text = stringResource(R.string.epg_source_own_guide_off),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                            )
                        }
                    }
                }

                IconButton(onClick = onRefresh) {
                    Icon(CinemaIcons.Refresh, stringResource(R.string.common_refresh))
                }
            }

            if (activeProgress != null) {
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xxs)) {
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
                        modifier = Modifier.fillMaxWidth().height(CinemaSpacing.xxs),
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

                    // When the last run confirmed no change, the stored download/ingest
                    // durations are from whenever it last actually ran — showing them here would
                    // read as work that didn't happen this time, so a single status pair
                    // replaces them instead.
                    val stats =
                        listOf(
                            stringResource(R.string.epg_source_stat_label) to lastIngested,
                        ) +
                            if (wasUnchanged) {
                                listOf("" to stringResource(R.string.epg_source_stat_unchanged))
                            } else {
                                listOf(
                                    stringResource(R.string.epg_source_stat_download) to
                                        NumberUtils.formatDuration(source.lastDownloadDurationMs),
                                    stringResource(R.string.epg_source_stat_ingest) to
                                        NumberUtils.formatDuration(source.lastIngestionDurationMs),
                                )
                            } +
                            listOf(
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

private val secretQueryValue = Regex("""([?&;](?:password|pass|pwd|token|api_key|api-key|access_token)=)[^&#]+""", RegexOption.IGNORE_CASE)

/**
 * A guide source's URL shortened to what tells same-label rows apart (M-10): host[:port], then
 * the last path segment and the query, with password-like values masked and any user:pass@
 * dropped. Unparseable input comes back with the same masking.
 */
internal fun guideSourceUrlHint(url: String): String {
    val uri = runCatching { java.net.URI(url.trim()) }.getOrNull()
    val host = uri?.rawAuthority?.substringAfterLast('@')
    val tail =
        listOfNotNull(
            uri?.rawPath?.substringAfterLast('/')?.takeIf { it.isNotEmpty() },
            uri?.rawQuery?.let { "?$it" },
        ).joinToString("")
    val hint =
        when {
            host.isNullOrEmpty() -> url.replace(Regex("""://[^/?#@]+@"""), "://")
            tail.isEmpty() -> host
            else -> "$host … $tail"
        }
    return hint.replace(secretQueryValue, "$1•••")
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

    androidx.compose.foundation.Canvas(modifier = Modifier.size(CinemaSpacing.xs + CinemaSpacing.xxxs)) {
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
        title = {
            Text(
                if (initialSource ==
                    null
                ) {
                    stringResource(R.string.epg_add_source_title)
                } else {
                    stringResource(R.string.epg_edit_source_title)
                },
            )
        },
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
