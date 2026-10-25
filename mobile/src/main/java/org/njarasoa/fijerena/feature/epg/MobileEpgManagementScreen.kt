package org.njarasoa.fijerena.feature.epg

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import org.njarasoa.fijerena.core.ui.components.CinemaProgressBar
import org.njarasoa.fijerena.core.ui.components.CinemaSwitch
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaTextField
import org.njarasoa.fijerena.core.ui.components.FieldKeyboards
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.theme.*
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.settings.components.GuideMaintenanceRow
import org.njarasoa.fijerena.feature.settings.components.SettingsPickerDialog
import org.njarasoa.fijerena.navigation.LocalDockInset
import org.njarasoa.fijerena.ui.components.MobileEmptyState
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaTextButton
import org.njarasoa.fijerena.ui.components.buttons.IconAction

/**
 * One source's guide sources (A-9, M5): the list first, its bulk actions, an empty state that
 * offers to add one. Each card shows its own auto-refresh, changed from its Auto-refresh button
 * (P5b); guide maintenance is device-wide and lives in Settings.
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
                    SettingsViewModelFactory(context.applicationContext)
                },
        )

    // null until the first emission, so the empty state doesn't flash while the list loads.
    val sources by viewModel.sources.collectAsStateWithLifecycle(initialValue = null)
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
            viewModel.userMessage.collect { message ->
                android.widget.Toast
                    .makeText(context, message.asString(context), android.widget.Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<EpgSourceEntity?>(null) }
    var intervalSource by remember { mutableStateOf<EpgSourceEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    CinemaText(
                        providerName?.let { stringResource(R.string.epg_management_title_format, it) }
                            ?: stringResource(R.string.epg_sources_header),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        role = TextRole.Title,
                        tone = TextTone.Inherit,
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
            // No guide sources: the shared empty state, centred, with the one action.
            if (sources?.isEmpty() == true) {
                MobileEmptyState(
                    message = stringResource(R.string.epg_summary_no_sources),
                    actionLabel = stringResource(R.string.epg_add_source),
                    onAction = { showAddDialog = true },
                )
                return@Box
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = CinemaSpacing.md,
                        top = CinemaSpacing.md,
                        end = CinemaSpacing.md,
                        bottom =
                            CinemaSpacing.md + LocalDockInset.current,
                    ),
                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
            ) {
                if (staleSourceCount > 0 || failedSourceCount > 0) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
                        ) {
                            if (staleSourceCount > 0) {
                                CinemaButton(
                                    onClick = { viewModel.refreshStale() },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    CinemaText(
                                        stringResource(R.string.epg_refresh_stale_btn, staleSourceCount),
                                        role = TextRole.Label,
                                        tone = TextTone.Inherit,
                                    )
                                }
                            }
                            if (failedSourceCount > 0) {
                                CinemaButton(
                                    onClick = { viewModel.refreshFailed() },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    CinemaText(
                                        stringResource(R.string.epg_retry_failed_btn, failedSourceCount),
                                        role = TextRole.Label,
                                        tone = TextTone.Inherit,
                                    )
                                }
                            }
                        }
                    }
                }

                items(sources.orEmpty(), key = { it.id }) { source ->
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
                        latestProgrammeTime = latestTime,
                        activeProgress = activeProgress,
                        wasUnchanged = wasUnchanged,
                        isOwnGuide = provider?.let { AutoXmltvSources.isAutoXmltvSource(source, it.url) } == true,
                        nowMs = nowMs,
                        staleThresholdMs = viewModel.staleThresholdMs(source),
                        refreshSummary =
                            EpgManagementViewModel
                                .refreshSummary(
                                    source.enabled,
                                    viewModel.refreshIntervalHours(source),
                                ).asString(),
                        onRefresh = { viewModel.refreshSource(source) },
                        onAutoRefresh = { intervalSource = source },
                        onEdit = { editingSource = source },
                        onDelete = { viewModel.deleteSource(source.id) },
                        onEnabledChange = { viewModel.setSourceEnabled(source, it) },
                    )
                }
                // The guide data of every source, as in Settings' Sources & guide (Settings reorg, Phase 3).
                item { GuideMaintenanceRow(viewModel = viewModel) }
            }
        }
    }

    if (showAddDialog) {
        EpgSourceEditDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { url, label, tz, method, enabled ->
                viewModel.addSource(url, label, tz, listOf(providerId), method, enabled)
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
    intervalSource?.let { source ->
        val current = viewModel.refreshIntervalHours(source)
        SettingsPickerDialog(
            title = stringResource(R.string.epg_auto_refresh_title),
            options =
                EpgManagementViewModel.refreshIntervalOptions(current).map { hours ->
                    EpgManagementViewModel.refreshIntervalLabel(hours).asString() to hours
                },
            selected = current,
            onSelect = { hours ->
                if (hours != current) viewModel.setRefreshInterval(source.id, hours)
                intervalSource = null
            },
            onDismiss = { intervalSource = null },
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
    latestProgrammeTime: Long,
    activeProgress: org.njarasoa.fijerena.core.network.xmltv.EpgFileManager.ActiveSourceProgress?,
    wasUnchanged: Boolean,
    /** The source's own guide, whose switch is "Provides a guide" (Edit Source). */
    isOwnGuide: Boolean,
    nowMs: Long,
    staleThresholdMs: Long,
    refreshSummary: String,
    onRefresh: () -> Unit,
    onAutoRefresh: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    GlassPanel {
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
                    val useLabel = stringResource(R.string.epg_source_use_label)
                    CinemaSwitch(
                        checked = source.enabled,
                        onCheckedChange = onEnabledChange,
                        modifier = Modifier.semantics { contentDescription = useLabel },
                    )

                    StatusIndicator(source, nowMs, staleThresholdMs)

                    Column {
                        CinemaText(
                            text = source.label.ifBlank { stringResource(R.string.epg_unnamed_source) },
                            role = TextRole.Heading,
                            tone = TextTone.Inherit,
                        )
                        CinemaText(
                            text = guideSourceUrlHint(source.url),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            role = TextRole.BodySmall,
                            tone = TextTone.Secondary,
                        )
                        if (isOwnGuide) {
                            CinemaText(
                                text = stringResource(R.string.epg_source_own_guide_hint),
                                role = TextRole.BodySmall,
                                tone = TextTone.Secondary,
                            )
                        }
                        CinemaText(
                            text = refreshSummary,
                            role = TextRole.BodySmall,
                            tone = TextTone.Secondary,
                        )
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
                        CinemaText(
                            text = stringResource(R.string.epg_source_phase_format, localizedEpgPhase(activeProgress.phase)),
                            role = TextRole.Caption,
                            tone = TextTone.Accent,
                        )
                        CinemaText(
                            // No size from the server (panel-a sends none): the bytes so far instead of a percentage.
                            text =
                                if (activeProgress.progressPercent >= 0) {
                                    stringResource(R.string.epg_source_progress_percent, activeProgress.progressPercent)
                                } else {
                                    NumberUtils.formatBytes(activeProgress.downloadedBytes)
                                },
                            role = TextRole.Caption,
                            tone = TextTone.Accent,
                        )
                    }
                    if (activeProgress.progressPercent >= 0) {
                        CinemaProgressBar(
                            progress = { activeProgress.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth().height(CinemaSpacing.xxs),
                        )
                    } else {
                        CinemaProgressBar(progress = null, modifier = Modifier.fillMaxWidth().height(CinemaSpacing.xxs))
                    }
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
                CinemaText(
                    text = stringResource(R.string.epg_database_error, lastError),
                    modifier = Modifier.padding(top = CinemaSpacing.xs),
                    role = TextRole.Caption,
                    tone = TextTone.Danger,
                )
            }

            Spacer(modifier = Modifier.height(CinemaSpacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAction(
                    onClick = onAutoRefresh,
                    icon = CinemaIcons.Sync,
                    label = stringResource(R.string.epg_auto_refresh_title),
                )
                IconAction(
                    onClick = onEdit,
                    icon = CinemaIcons.Edit,
                    label = stringResource(R.string.provider_edit_button),
                )
                IconAction(
                    onClick = { showDeleteConfirm = true },
                    icon = CinemaIcons.Delete,
                    label = stringResource(R.string.provider_delete_button),
                    tint = CinemaTheme.colors.danger,
                )
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
                    kind = ButtonKind.Danger,
                ) {
                    CinemaText(
                        stringResource(R.string.provider_delete_button),
                        role = TextRole.Label,
                        tone = TextTone.Inherit,
                    )
                }
            },
            dismissButton = {
                CinemaDialogActionButton(
                    onClick = { showDeleteConfirm = false },
                    kind = ButtonKind.Other,
                ) {
                    CinemaText(
                        stringResource(R.string.common_cancel),
                        role = TextRole.Label,
                        tone = TextTone.Inherit,
                    )
                }
            },
            title = {
                CinemaText(
                    stringResource(R.string.epg_delete_source_title),
                    role = TextRole.Title,
                    tone = TextTone.Inherit,
                )
            },
            text = {
                CinemaText(
                    stringResource(R.string.epg_delete_source_confirm_message, source.label),
                    role = TextRole.Body,
                    tone = TextTone.Inherit,
                )
            },
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
            !source.enabled -> CinemaTheme.colors.textDisabled
            source.lastError != null -> CinemaTheme.colors.danger
            source.lastIngestedAtMs == 0L -> CinemaTheme.colors.textDisabled
            nowMs - source.lastIngestedAtMs > staleThresholdMs -> CinemaTheme.colors.warning
            else -> CinemaTheme.colors.success
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
        CinemaText(
            text = label,
            role = TextRole.Caption,
            tone = TextTone.Secondary,
        )
        CinemaText(
            text = value,
            role = TextRole.BodySmall,
            tone = TextTone.Inherit,
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
            CinemaText(
                if (initialSource ==
                    null
                ) {
                    stringResource(R.string.epg_add_source_title)
                } else {
                    stringResource(R.string.epg_edit_source_title)
                },
                role = TextRole.Title,
                tone = TextTone.Inherit,
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
                kind = ButtonKind.Main,
            ) {
                CinemaText(
                    stringResource(R.string.provider_save_button),
                    role = TextRole.Label,
                    tone = TextTone.Inherit,
                )
            }
        },
        dismissButton = {
            CinemaDialogActionButton(onClick = onDismiss, kind = ButtonKind.Other) {
                CinemaText(
                    stringResource(R.string.common_cancel),
                    role = TextRole.Label,
                    tone = TextTone.Inherit,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
                CinemaTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = stringResource(R.string.epg_xmltv_url_label),
                    keyboardOptions = FieldKeyboards.Url,
                    supportingText = stringResource(R.string.epg_xmltv_url_hint),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                CinemaTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = stringResource(R.string.epg_label_optional),
                    keyboardOptions = FieldKeyboards.Name,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                CinemaTextField(
                    value = tzOffset,
                    onValueChange = { tzOffset = it },
                    label = stringResource(R.string.epg_timezone_label),
                    keyboardOptions = FieldKeyboards.SignedNumber,
                    supportingText = stringResource(R.string.epg_timezone_hint),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
    )
}
