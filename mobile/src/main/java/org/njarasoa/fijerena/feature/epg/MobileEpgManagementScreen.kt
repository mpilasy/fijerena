package org.njarasoa.fijerena.feature.epg

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager.MultiSourceState
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaCheckbox
import org.njarasoa.fijerena.core.ui.components.CinemaProgressBar
import org.njarasoa.fijerena.core.ui.components.CinemaSwitch
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaTextField
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.FieldKeyboards
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.SectionHeader
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.*
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.utils.AppNotices
import org.njarasoa.fijerena.core.ui.utils.DateText
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.utils.storedErrorText
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.settings.components.GuideMaintenanceRow
import org.njarasoa.fijerena.feature.settings.components.SettingsPickerDialog
import org.njarasoa.fijerena.navigation.LocalDockInset
import org.njarasoa.fijerena.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.ui.components.CinemaCheckRow
import org.njarasoa.fijerena.ui.components.MobileEmptyState
import org.njarasoa.fijerena.ui.components.MobileScreenHeader
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaTextButton
import org.njarasoa.fijerena.ui.components.buttons.IconAction

/**
 * Every guide source on one page (A-9, M5; the one guide source per URL plan, OG-D11): one card
 * for each guide source with the sources it is used for, then the sources with no guide source,
 * each with its own Add, then guide maintenance; an empty state that offers to add one. Each card
 * shows its own auto-refresh, changed from its Auto-refresh button (P5b). [providerId] scrolls the
 * page to the first card that source uses, else to its Add; 0 opens it at the top.
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

    // null until the first emission, so the empty state doesn't flash while the list loads. One
    // card per guide source (the one guide source per URL plan).
    val page by viewModel.page.collectAsStateWithLifecycle(initialValue = null)
    val cards = page?.cards
    val latestProgrammeTimes by viewModel.latestProgrammeTimes.collectAsStateWithLifecycle()
    val busySourceIds by viewModel.busySourceIds.collectAsStateWithLifecycle()
    val staleSourceCount by viewModel.staleSourceCount.collectAsStateWithLifecycle()
    val cellularDialog by viewModel.cellularDialog.collectAsStateWithLifecycle()
    val failedSourceCount by viewModel.failedSourceCount.collectAsStateWithLifecycle()
    val storageLowFreeBytes by viewModel.storageLowFreeBytes.collectAsStateWithLifecycle()
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()
    val cardList = cards.orEmpty()
    val withoutGuide = page?.sourcesWithoutGuide.orEmpty()
    val guideProviders = page?.providers.orEmpty()

    val nowMs = remember { System.currentTimeMillis() }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.userMessage.collect { message ->
                AppNotices.show(message.asString(context))
            }
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    // The source whose own Add (under Sources Without a Guide Source) was pressed; null: the Add at the top.
    var addForSource by remember { mutableStateOf<Long?>(null) }
    var editingCard by remember { mutableStateOf<EpgManagementViewModel.GuideCard?>(null) }
    var intervalSource by remember { mutableStateOf<EpgSourceEntity?>(null) }

    // Opened on a source: once the list has loaded, scroll to the first card it uses, else to its Add.
    val listState = rememberLazyListState()
    LaunchedEffect(page != null) {
        if (cardList.isEmpty()) return@LaunchedEffect
        val cardAt = cardList.indexOfFirst { card -> card.usedBy.any { it.id == providerId } }
        val addAt = withoutGuide.indexOfFirst { it.id == providerId }
        when {
            cardAt > 0 -> listState.scrollToItem(cardAt)
            cardAt < 0 && addAt >= 0 -> listState.scrollToItem(cardList.size + 1 + addAt)
        }
    }

    Scaffold(
        topBar = {
            // The page's actions in the top bar, as on the other Settings pages: refresh the stale
            // sources and try the failed ones again (only when there are some), and Add.
            MobileScreenHeader(title = stringResource(R.string.epg_management_screen_title), onBack = onBack) {
                if (staleSourceCount > 0) {
                    IconAction(
                        onClick = { viewModel.refreshStale() },
                        icon = CinemaIcons.Refresh,
                        label = stringResource(R.string.epg_refresh_stale_btn, staleSourceCount),
                        tone = TextTone.Warning,
                    )
                }
                if (failedSourceCount > 0) {
                    IconAction(
                        onClick = { viewModel.refreshFailed() },
                        icon = CinemaIcons.Replay,
                        label = stringResource(R.string.epg_retry_failed_btn, failedSourceCount),
                        tone = TextTone.Warning,
                    )
                }
                IconAction(onClick = { showAddDialog = true }, icon = CinemaIcons.Add, label = stringResource(R.string.epg_add_source))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Storage is almost full: no refresh starts, so say why.
            storageLowFreeBytes?.let { free ->
                CinemaText(
                    text = stringResource(R.string.epg_storage_low_note, NumberUtils.formatBytes(LocalResources.current, free)),
                    role = TextRole.Caption,
                    tone = TextTone.Warning,
                    modifier = Modifier.padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.sm),
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                // No guide sources: the shared empty state, centred, with the one action.
                if (cards?.isEmpty() == true) {
                    MobileEmptyState(
                        message = stringResource(R.string.epg_summary_no_sources),
                        actionLabel = stringResource(R.string.epg_add_source),
                        onAction = { showAddDialog = true },
                    )
                    return@Box
                }
                LazyColumn(
                    state = listState,
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
                    items(cardList, key = { it.source.id }, contentType = { "source" }) { card ->
                        val unnamed = stringResource(R.string.epg_unnamed_source)
                        val source = card.source
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
                            card = card,
                            latestProgrammeTime = latestTime,
                            activeProgress = activeProgress,
                            waiting = source.id in busySourceIds,
                            waitingFor =
                                EpgManagementViewModel
                                    .waitingFor(cardList, EpgManagementViewModel.workingOn(processingState), source.id)
                                    .joinToString { it.label.ifBlank { unnamed } },
                            indexing =
                                (processingState as? MultiSourceState.Finalizing)?.indexingSourceIds?.contains(source.id) == true,
                            indexingWaits = (processingState as? MultiSourceState.Finalizing)?.waitingForPlayback == true,
                            wasUnchanged = wasUnchanged,
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
                            onEdit = { editingCard = card },
                            onDelete = { viewModel.deleteSource(source.id) },
                            onEnabledChange = { viewModel.setSourceEnabled(card, it) },
                        )
                    }
                    if (withoutGuide.isNotEmpty()) {
                        item(key = "without-guide", contentType = "heading") {
                            SectionHeader(
                                title = stringResource(R.string.epg_sources_without_guide_title),
                                modifier = Modifier.padding(top = CinemaSpacing.sm),
                            )
                        }
                        // Each source with no guide source, with its own Add (that source checked in Use for).
                        items(withoutGuide, key = { "add-${it.id}" }, contentType = { "add" }) { provider ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                CinemaText(
                                    text = provider.name,
                                    role = TextRole.Heading,
                                    tone = TextTone.Inherit,
                                    modifier = Modifier.weight(1f),
                                )
                                IconAction(
                                    onClick = {
                                        addForSource = provider.id
                                        showAddDialog = true
                                    },
                                    icon = CinemaIcons.Add,
                                    label = stringResource(R.string.epg_add_source),
                                )
                            }
                        }
                    }
                    // The guide data of every source, as in Settings' Sources & guide (Settings reorg, Phase 3).
                    item { GuideMaintenanceRow(viewModel = viewModel) }
                }
            }
        }
    }

    // On mobile data a refresh waits for this answer (EpgManagementViewModel.cellularDialog).
    cellularDialog?.let { dialog ->
        CinemaAlertDialog(
            onDismissRequest = dialog.onDismiss,
            main = DialogAction(stringResource(R.string.common_refresh), onClick = dialog.onConfirm),
            cancel = DialogAction(stringResource(R.string.common_cancel), onClick = dialog.onDismiss),
            title = stringResource(R.string.epg_cellular_confirm_title),
            text = { CinemaText(stringResource(R.string.epg_cellular_confirm_message), tone = TextTone.Inherit) },
        )
    }

    if (showAddDialog) {
        EpgSourceEditDialog(
            onDismiss = {
                showAddDialog = false
                addForSource = null
            },
            onConfirm = { url, label, tz, method, enabled, forSources ->
                viewModel.addSource(url, label, tz, forSources, method, enabled)
                showAddDialog = false
                addForSource = null
            },
            // Use for: the source whose own Add was pressed, else the source the page was opened
            // on, else the first.
            forSources = guideProviders,
            initialFor =
                setOfNotNull(
                    addForSource ?: guideProviders.firstOrNull { it.id == providerId }?.id ?: guideProviders.firstOrNull()?.id,
                ),
        )
    }

    editingCard?.let { card ->
        val source = card.source
        EpgSourceEditDialog(
            initialSource = source,
            onDismiss = { editingCard = null },
            onConfirm = { url, label, tz, method, enabled, forSources ->
                viewModel.updateSource(
                    source.copy(url = url, label = label, timezoneOffsetHours = tz, ingestMethod = method, enabled = enabled),
                    forSources,
                )
                editingCard = null
            },
            forSources = guideProviders,
            initialFor = card.usedBy.mapTo(HashSet()) { it.id },
            ownGuideOf = card.ownGuideOf,
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
        EpgFileManager.PHASE_INDEXING -> stringResource(R.string.epg_phase_indexing)
        EpgFileManager.PHASE_INDEXING_WAITS -> stringResource(R.string.epg_phase_indexing_waits)
        else -> phase
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpgSourceCard(
    card: EpgManagementViewModel.GuideCard,
    latestProgrammeTime: Long,
    activeProgress: org.njarasoa.fijerena.core.network.xmltv.EpgFileManager.ActiveSourceProgress?,
    /** A refresh was asked for and has not reached this guide source yet: "Waiting to refresh". */
    waiting: Boolean,
    /** The names of the guide sources a refresh is working on now, that a waiting card waits for; "" when none. */
    waitingFor: String,
    /** The guide index is taking in this guide source's import: "Indexing". */
    indexing: Boolean,
    /** That indexing waits until video stops playing. */
    indexingWaits: Boolean,
    wasUnchanged: Boolean,
    nowMs: Long,
    staleThresholdMs: Long,
    refreshSummary: String,
    onRefresh: () -> Unit,
    onAutoRefresh: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    val source = card.source
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
                    // look-ok: card control: the switch turns the guide source on or off
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
                        CinemaText(
                            text = stringResource(R.string.epg_source_used_for, card.usedBy.joinToString(", ") { it.name }),
                            role = TextRole.BodySmall,
                            tone = TextTone.Secondary,
                        )
                        if (!card.usedByProfile) {
                            CinemaText(
                                text = stringResource(R.string.provider_not_in_profile),
                                role = TextRole.BodySmall,
                                tone = TextTone.Secondary,
                            )
                        }
                        // The source's own guide, whose switch is "Provides a guide" (Edit Source).
                        if (card.ownGuideOf != null) {
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
            }

            if (activeProgress == null && (indexing || waiting)) {
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xxs)) {
                    CinemaText(
                        text =
                            stringResource(
                                R.string.epg_source_phase_format,
                                when {
                                    indexing && indexingWaits -> stringResource(R.string.epg_phase_indexing_waits)
                                    indexing -> stringResource(R.string.epg_phase_indexing)
                                    waitingFor.isNotEmpty() -> stringResource(R.string.epg_phase_waiting_for, waitingFor)
                                    else -> stringResource(R.string.epg_phase_waiting_to_refresh)
                                },
                            ),
                        role = TextRole.Caption,
                        tone = TextTone.Accent,
                    )
                    CinemaProgressBar(progress = null, modifier = Modifier.fillMaxWidth().height(CinemaSpacing.xxs))
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
                                    NumberUtils.formatBytes(LocalResources.current, activeProgress.downloadedBytes)
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
                            DateText.dateTime(LocalContext.current, source.lastIngestedAtMs)
                        } else {
                            stringResource(R.string.epg_source_never)
                        }

                    val latestProgStr =
                        if (latestProgrammeTime > 0) {
                            DateText.dateTime(LocalContext.current, latestProgrammeTime * 1000L)
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
                                        NumberUtils.formatDuration(LocalResources.current, source.lastDownloadDurationMs),
                                    stringResource(R.string.epg_source_stat_ingest) to
                                        NumberUtils.formatDuration(LocalResources.current, source.lastIngestionDurationMs),
                                    stringResource(R.string.epg_source_stat_index) to
                                        NumberUtils.formatDuration(LocalResources.current, source.lastIndexDurationMs),
                                )
                            } +
                            listOf(
                                stringResource(R.string.epg_source_stat_latest) to latestProgStr,
                                stringResource(R.string.epg_source_stat_channels) to
                                    NumberUtils.formatCount(LocalResources.current, source.lastChannels),
                                stringResource(R.string.epg_source_stat_programmes) to
                                    NumberUtils.formatCount(LocalResources.current, source.lastProgrammes),
                            ) +
                            // The last refresh failed: its time and the failures since the last
                            // success; the reason follows under the stats.
                            if (source.lastError != null) {
                                listOfNotNull(
                                    source.lastFailedAtMs?.let { failedAt ->
                                        stringResource(R.string.epg_source_stat_last_failure) to
                                            DateText.dateTime(LocalContext.current, failedAt)
                                    },
                                    (
                                        stringResource(R.string.epg_source_stat_failures) to
                                            NumberUtils.formatCount(LocalResources.current, source.failuresSinceSuccess)
                                    ).takeIf { source.failuresSinceSuccess > 0 },
                                )
                            } else {
                                emptyList()
                            }
                    // Two stats in each row, each one half of the card: a date has room for one or
                    // two lines, also on a narrow phone.
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
                    ) {
                        stats.chunked(2).forEach { rowStats ->
                            Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.md)) {
                                rowStats.forEach { (label, value) -> SourceStat(label, value, Modifier.weight(1f)) }
                                if (rowStats.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            val lastError = source.lastError
            if (lastError != null) {
                CinemaText(
                    text = stringResource(R.string.epg_database_error, storedErrorText(lastError)),
                    modifier = Modifier.padding(top = CinemaSpacing.xs),
                    role = TextRole.Caption,
                    tone = TextTone.Danger,
                )
            }

            Spacer(modifier = Modifier.height(CinemaSpacing.sm))

            // The card's actions, in the same order and tones as on TV: Refresh, Edit, Auto Refresh, Delete.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAction(onClick = onRefresh, icon = CinemaIcons.Refresh, label = stringResource(R.string.common_refresh))
                IconAction(
                    onClick = onEdit,
                    icon = CinemaIcons.Edit,
                    label = stringResource(R.string.provider_edit_button),
                )
                IconAction(
                    onClick = onAutoRefresh,
                    icon = CinemaIcons.Schedule,
                    label = stringResource(R.string.epg_auto_refresh_title),
                )
                IconAction(
                    onClick = { showDeleteConfirm = true },
                    icon = CinemaIcons.Delete,
                    label = stringResource(R.string.provider_delete_button),
                    tone = TextTone.Danger,
                )
            }
        }
    }

    if (showDeleteConfirm) {
        CinemaAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            danger =
                DialogAction(stringResource(R.string.provider_delete_button)) {
                    onDelete()
                    showDeleteConfirm = false
                },
            cancel = DialogAction(stringResource(R.string.common_cancel)) { showDeleteConfirm = false },
            title = stringResource(R.string.epg_delete_source_confirm_title),
            text = {
                val name = source.label.ifBlank { source.url }
                CinemaText(
                    // Several sources use it: it goes for all of them (OG-Q8), so the text names them.
                    if (card.usedBy.size > 1) {
                        stringResource(
                            R.string.epg_delete_shared_source_confirm_message,
                            name,
                            card.usedBy.joinToString(", ") { it.name },
                        )
                    } else {
                        stringResource(R.string.epg_delete_source_confirm_message, name)
                    },
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
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
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
    onConfirm: (url: String, label: String, tz: Int, method: String, enabled: Boolean, forSources: Set<Long>) -> Unit,
    // The sources the guide source can be for, and the ones checked; the list shows with two or
    // more, on Add and on Edit of a hand-added guide source.
    forSources: List<ProviderEntity> = emptyList(),
    initialFor: Set<Long> = emptySet(),
    // Editing a source's own guide: it is for that source only (OG-D3), shown as text.
    ownGuideOf: ProviderEntity? = null,
) {
    var url by remember { mutableStateOf(initialSource?.url ?: "") }
    var checkedFor by remember { mutableStateOf(initialFor) }
    var label by remember { mutableStateOf(initialSource?.label ?: "") }
    var tzOffset by remember { mutableStateOf(initialSource?.timezoneOffsetHours?.toString() ?: "0") }
    val showUseFor = ownGuideOf == null && forSources.size > 1

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(if (initialSource == null) R.string.epg_add_source_title else R.string.epg_edit_source_title),
        main =
            DialogAction(
                stringResource(R.string.provider_save_button),
                // At least one source: Add always, Edit when it shows Use for.
                enabled =
                    url.isNotBlank() && ((initialSource != null && !showUseFor) || checkedFor.isNotEmpty()),
            ) {
                onConfirm(
                    url,
                    label,
                    tzOffset.toIntOrNull() ?: 0,
                    initialSource?.ingestMethod ?: "DOWNLOADED",
                    initialSource?.enabled ?: true,
                    if (showUseFor || initialSource == null) checkedFor else emptySet(),
                )
            },
        cancel = DialogAction(stringResource(R.string.common_cancel), onClick = onDismiss),
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
                if (ownGuideOf != null) {
                    CinemaText(
                        stringResource(R.string.epg_source_used_for, ownGuideOf.name),
                        role = TextRole.BodySmall,
                        tone = TextTone.Secondary,
                    )
                } else if (showUseFor) {
                    CinemaText(stringResource(R.string.epg_use_for_label), role = TextRole.Label, tone = TextTone.Secondary)
                    forSources.forEach { source ->
                        val checked = source.id in checkedFor
                        CinemaCheckRow(
                            checked = checked,
                            onCheckedChange = { on -> checkedFor = if (on) checkedFor + source.id else checkedFor - source.id },
                            label = source.name,
                        )
                    }
                }
            }
        },
    )
}
