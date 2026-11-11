package org.njarasoa.fijerena.feature.epg

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager.MultiSourceState
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaProgressBar
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.FieldKeyboards
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.SectionHeader
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.epg.guideClearFraction
import org.njarasoa.fijerena.core.ui.epg.guideClearText
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.utils.AppNotices
import org.njarasoa.fijerena.core.ui.utils.DateText
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.utils.storedErrorText
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.provider.components.ProviderDangerButton
import org.njarasoa.fijerena.feature.settings.components.GuideMaintenancePane
import org.njarasoa.fijerena.feature.settings.components.GuideMaintenanceRow
import org.njarasoa.fijerena.feature.settings.components.PickerOption
import org.njarasoa.fijerena.feature.settings.components.SettingsPickerPane
import org.njarasoa.fijerena.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.ui.components.ReadOnlyFieldWithEdit
import org.njarasoa.fijerena.ui.components.TvEmptyState
import org.njarasoa.fijerena.ui.components.TvScreenHeader
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction
import org.njarasoa.fijerena.ui.components.input.TvCheckRow
import org.njarasoa.fijerena.ui.components.input.TvSwitchButton
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * Every guide source on one page (A-9, T6; the one guide source per URL plan, OG-D11): the bulk
 * actions, one card per guide source with the sources it is used for, then the sources with no
 * guide source, each with its own Add; an empty state offers to add one. Each card's Auto-refresh
 * button opens a picker in place of the list (P5b). Guide maintenance is device-wide: its row is
 * at the foot of this list and in Settings (Sources & guide).
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun TvEpgManagementScreen(
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

    // null until the first emission, so the empty state doesn't flash (or take focus) while the
    // list loads. One card per guide source (the one guide source per URL plan).
    val page by viewModel.page.collectAsStateWithLifecycle(initialValue = null)
    val cards = page?.cards
    val latestProgrammeTimes by viewModel.latestProgrammeTimes.collectAsStateWithLifecycle()
    val clearing by viewModel.clearing.collectAsStateWithLifecycle()
    val staleSourceCount by viewModel.staleSourceCount.collectAsStateWithLifecycle()
    val cellularDialog by viewModel.cellularDialog.collectAsStateWithLifecycle()
    val failedSourceCount by viewModel.failedSourceCount.collectAsStateWithLifecycle()
    val storageLowFreeBytes by viewModel.storageLowFreeBytes.collectAsStateWithLifecycle()
    // Refresh stale / Retry failed, above the groups, only while there is something to refresh.
    val refreshRow = staleSourceCount > 0 || failedSourceCount > 0
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()
    val busySourceIds by viewModel.busySourceIds.collectAsStateWithLifecycle()

    val nowMs = remember { System.currentTimeMillis() }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { message ->
            AppNotices.show(message.asString(context))
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    // The source whose own Add (under Sources Without a Guide Source) was pressed; null: the Add at the top.
    var addForSource by remember { mutableStateOf<Long?>(null) }
    var editingCard by remember { mutableStateOf<EpgManagementViewModel.GuideCard?>(null) }
    var deletingCard by remember { mutableStateOf<EpgManagementViewModel.GuideCard?>(null) }
    var clearingCard by remember { mutableStateOf<EpgManagementViewModel.GuideCard?>(null) }

    val scale = LocalUiScale.current
    val listState = rememberLazyListState()
    val cardList = cards.orEmpty()
    val withoutGuide = page?.sourcesWithoutGuide.orEmpty()
    val guideProviders = page?.providers.orEmpty()

    // Entry focus once the list has loaded: opened on a source ([providerId]), the first card it uses
    // (scrolled into view), else its Add under Sources Without a Guide Source; opened at the top, the
    // first card. TvEmptyState focuses its action itself (the TV focus contract, plan Part I, B).
    val entryRowFocus = remember { FocusRequester() }
    val addFocus = remember { FocusRequester() }
    val entryAddRowFocus = remember { FocusRequester() }
    var entrySourceId by remember { mutableStateOf<Long?>(null) }
    val loaded = page != null
    LaunchedEffect(loaded) {
        if (!loaded || cardList.isEmpty()) return@LaunchedEffect
        val cardAt = cardList.indexOfFirst { card -> card.usedBy.any { it.id == providerId } }
        val addAt = withoutGuide.indexOfFirst { it.id == providerId }
        if (cardAt < 0 && addAt >= 0) {
            listState.scrollToItem(addRowIndex(cardList.size, addAt, refreshRow))
            entryAddRowFocus.requestFocusWithRetry()
        } else {
            entrySourceId = cardList[maxOf(cardAt, 0)].source.id
            if (cardAt > 0) listState.scrollToItem(cardIndex(cardAt, refreshRow))
            entryRowFocus.requestFocusWithRetry()
        }
    }

    // A source's auto-refresh picker (P5b), drawn in place of the list like Settings' choice
    // pickers; on close, focus goes back to that row's Auto-refresh button, and the list keeps its
    // scroll through [listState].
    var intervalSource by remember { mutableStateOf<EpgSourceEntity?>(null) }
    var intervalReturnId by remember { mutableStateOf<Long?>(null) }
    val intervalButtonFocus = remember { FocusRequester() }
    LaunchedEffect(intervalSource) {
        if (intervalSource == null && intervalReturnId != null) {
            intervalButtonFocus.requestFocusWithRetry()
            intervalReturnId = null
        }
    }
    // Guide data maintenance, drawn in place of the list as the interval picker is; on close, focus
    // goes back to its row.
    var showMaintenance by remember { mutableStateOf(false) }
    var maintenanceReturn by remember { mutableStateOf(false) }
    val maintenanceFocus = remember { FocusRequester() }
    LaunchedEffect(showMaintenance) {
        if (!showMaintenance && maintenanceReturn) {
            maintenanceFocus.requestFocusWithRetry()
            maintenanceReturn = false
        }
    }
    if (showMaintenance) {
        Box(modifier = Modifier.padding(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical)) {
            GuideMaintenancePane(
                viewModel = viewModel,
                onBack = {
                    maintenanceReturn = true
                    showMaintenance = false
                },
            )
        }
        return
    }

    val pickerSource = intervalSource
    if (pickerSource != null) {
        val current = viewModel.refreshIntervalHours(pickerSource)
        SettingsPickerPane(
            title =
                stringResource(R.string.epg_auto_refresh_title) + " · " +
                    pickerSource.label.ifBlank { stringResource(R.string.epg_unnamed_source) },
            options =
                EpgManagementViewModel.refreshIntervalOptions(current).map { hours ->
                    PickerOption(EpgManagementViewModel.refreshIntervalLabel(hours).asString(), hours)
                },
            selectedValue = current,
            onPick = { hours -> if (hours != current) viewModel.setRefreshInterval(pickerSource.id, hours) },
            onBack = {
                intervalReturnId = pickerSource.id
                intervalSource = null
            },
            modifier = Modifier.padding(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical),
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = Spacing.tvSafeMarginHorizontal,
                        vertical = Spacing.tvSafeMarginVertical,
                    ),
        ) {
            // Add is an icon action in the header, as on every Settings page (Sources, Live Sync).
            TvScreenHeader(title = stringResource(R.string.epg_management_screen_title)) {
                if (cards?.isEmpty() == false) {
                    TvIconAction(
                        onClick = { showAddDialog = true },
                        icon = CinemaIcons.Add,
                        label = stringResource(R.string.epg_add_source),
                        modifier = Modifier.focusRequester(addFocus),
                    )
                }
            }

            // Storage is almost full: no refresh starts, so say why.
            storageLowFreeBytes?.let { free ->
                CinemaText(
                    text = stringResource(R.string.epg_storage_low_note, NumberUtils.formatBytes(LocalResources.current, free)),
                    role = TextRole.Caption,
                    tone = TextTone.Warning,
                    modifier = Modifier.padding(bottom = Spacing.sm.scaled(scale)),
                )
            }

            // No guide source: one sentence and the one thing to do here, centred (TV UI audit #20).
            // The action takes focus itself; Left from it goes to the navigation rail (leftToRail
            // here reaches the action, the only focusable inside).
            if (cards?.isEmpty() == true) {
                TvEmptyState(
                    message = stringResource(R.string.epg_summary_no_sources),
                    icon = CinemaIcons.DateRange,
                    actionLabel = stringResource(R.string.epg_add_source),
                    onAction = { showAddDialog = true },
                    modifier = Modifier.leftToRail(),
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = Spacing.xs.scaled(scale)),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                ) {
                    if (cards != null && refreshRow) {
                        // Refreshing the stale or failed sources of this list.
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                            ) {
                                if (staleSourceCount > 0) {
                                    CinemaSecondaryButton(
                                        onClick = { viewModel.refreshStale() },
                                        text = stringResource(R.string.epg_refresh_stale_btn, staleSourceCount),
                                        modifier = Modifier.leftToRail(),
                                    )
                                }

                                if (failedSourceCount > 0) {
                                    CinemaSecondaryButton(
                                        onClick = { viewModel.refreshFailed() },
                                        text = stringResource(R.string.epg_retry_failed_btn, failedSourceCount),
                                        modifier = if (staleSourceCount > 0) Modifier else Modifier.leftToRail(),
                                    )
                                }
                            }
                        }
                    }

                    items(cardList, key = { it.source.id }, contentType = { "source" }) { card ->
                        val source = card.source
                        val latestTime = latestProgrammeTimes[source.id] ?: 0L

                        val activeProgress =
                            if (processingState is MultiSourceState.Processing) {
                                (processingState as MultiSourceState.Processing).activeProgress[source.id]
                            } else {
                                null
                            }

                        // The guide index is taking in this guide source's import.
                        val indexing =
                            (processingState as? MultiSourceState.Finalizing)?.indexingSourceIds?.contains(source.id) == true
                        // Indexing waits until video stops playing.
                        val indexingWaits = indexing && (processingState as? MultiSourceState.Finalizing)?.waitingForPlayback == true

                        // Whether the run that just finished found this source's data unchanged (304, or the same content
                        // hash). True only until the next processing state.
                        val wasUnchanged =
                            (processingState as? MultiSourceState.Completed)?.sourceStats?.get(source.id)?.unchanged == true

                        GlassPanel(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                                ) {
                                    // On/off: for the source's own guide this is "Provides a guide" (Edit Source).
                                    val useLabel = stringResource(R.string.epg_source_use_label)
                                    TvSwitchButton(
                                        checked = source.enabled,
                                        onToggle = { viewModel.setSourceEnabled(card, !source.enabled) },
                                        modifier =
                                            (if (source.id == entrySourceId) Modifier.focusRequester(entryRowFocus) else Modifier)
                                                .leftToRail()
                                                .semantics { contentDescription = useLabel },
                                    )

                                    StatusIndicator(source, nowMs, viewModel.staleThresholdMs(source), scale)

                                    Column(modifier = Modifier.weight(1f)) {
                                        CinemaText(
                                            text = source.label.ifBlank { stringResource(R.string.epg_unnamed_source) },
                                            role = TextRole.Heading,
                                        )
                                        CinemaText(
                                            text = guideSourceUrlHint(source.url),
                                            role = TextRole.BodySmall,
                                            tone = TextTone.Secondary,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        )
                                        GuideCardUse(card)
                                        CinemaText(
                                            text =
                                                EpgManagementViewModel
                                                    .refreshSummary(
                                                        source.enabled,
                                                        viewModel.refreshIntervalHours(source),
                                                    ).asString(),
                                            role = TextRole.BodySmall,
                                            tone = TextTone.Secondary,
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
                                    TvIconAction(
                                        onClick = { viewModel.refreshSource(source) },
                                        icon = CinemaIcons.Refresh,
                                        label = stringResource(R.string.common_refresh),
                                        modifier = Modifier.leftToRail(),
                                    )
                                    TvIconAction(
                                        onClick = { editingCard = card },
                                        icon = CinemaIcons.Edit,
                                        label = stringResource(R.string.provider_edit_button),
                                    )
                                    TvIconAction(
                                        onClick = { intervalSource = source },
                                        icon = CinemaIcons.Schedule,
                                        label = stringResource(R.string.epg_auto_refresh_title),
                                        modifier =
                                            if (source.id ==
                                                intervalReturnId
                                            ) {
                                                Modifier.focusRequester(intervalButtonFocus)
                                            } else {
                                                Modifier
                                            },
                                    )
                                    // A guide source that is off keeps its data; this frees its space.
                                    if (!source.enabled) {
                                        TvIconAction(
                                            onClick = { clearingCard = card },
                                            icon = CinemaIcons.ClearData,
                                            label = stringResource(R.string.epg_clear_source_data_button),
                                        )
                                    }
                                    TvIconAction(
                                        onClick = { deletingCard = card },
                                        icon = CinemaIcons.Delete,
                                        label = stringResource(R.string.provider_delete_button),
                                        danger = true,
                                    )
                                }

                                // Clear guide data is running on this guide source.
                                clearing[source.id]?.let { clearProgress ->
                                    Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs.scaled(scale))) {
                                        CinemaText(
                                            text = guideClearText(clearProgress),
                                            role = TextRole.Caption,
                                            tone = TextTone.Secondary,
                                        )
                                        val fraction = guideClearFraction(clearProgress)
                                        CinemaProgressBar(
                                            progress = fraction?.let { { it } },
                                            modifier = Modifier.fillMaxWidth().height(Spacing.xxs.scaled(scale)),
                                        )
                                    }
                                }
                                // Indexing, or a refresh was asked for and has not reached this guide source yet.
                                if (activeProgress == null && (indexing || source.id in busySourceIds)) {
                                    // The guide sources a refresh is working on now, named on a waiting card.
                                    val unnamed = stringResource(R.string.epg_unnamed_source)
                                    val waitingFor =
                                        EpgManagementViewModel
                                            .waitingFor(cardList, EpgManagementViewModel.workingOn(processingState), source.id)
                                            .joinToString { it.label.ifBlank { unnamed } }
                                    Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs.scaled(scale))) {
                                        CinemaText(
                                            text =
                                                stringResource(
                                                    R.string.epg_source_phase_format,
                                                    when {
                                                        indexingWaits -> {
                                                            stringResource(R.string.epg_phase_indexing_waits)
                                                        }

                                                        indexing -> {
                                                            stringResource(R.string.epg_phase_indexing)
                                                        }

                                                        waitingFor.isNotEmpty() -> {
                                                            stringResource(
                                                                R.string.epg_phase_waiting_for,
                                                                waitingFor,
                                                            )
                                                        }

                                                        else -> {
                                                            stringResource(R.string.epg_phase_waiting_to_refresh)
                                                        }
                                                    },
                                                ),
                                            role = TextRole.Caption,
                                            tone = TextTone.Secondary,
                                        )
                                        CinemaProgressBar(
                                            progress = null,
                                            modifier = Modifier.fillMaxWidth().height(Spacing.xxs.scaled(scale)),
                                        )
                                    }
                                }
                                if (activeProgress != null) {
                                    Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs.scaled(scale))) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            CinemaText(
                                                text =
                                                    stringResource(
                                                        R.string.epg_source_phase_format,
                                                        localizedEpgPhase(activeProgress.phase),
                                                    ),
                                                role = TextRole.Caption,
                                                tone = TextTone.Secondary,
                                            )
                                            CinemaText(
                                                // No size from the server (panel-a sends none): the bytes so far instead of a percentage.
                                                text =
                                                    if (activeProgress.progressPercent >= 0) {
                                                        stringResource(
                                                            R.string.epg_source_progress_percent,
                                                            activeProgress.progressPercent,
                                                        )
                                                    } else {
                                                        NumberUtils.formatBytes(LocalResources.current, activeProgress.downloadedBytes)
                                                    },
                                                role = TextRole.Caption,
                                                tone = TextTone.Secondary,
                                            )
                                        }
                                        if (activeProgress.progressPercent >= 0) {
                                            CinemaProgressBar(progress = {
                                                activeProgress.progressPercent / 100f
                                            }, modifier = Modifier.fillMaxWidth().height(Spacing.xxs.scaled(scale)))
                                        } else {
                                            CinemaProgressBar(
                                                progress = null,
                                                modifier = Modifier.fillMaxWidth().height(Spacing.xxs.scaled(scale)),
                                            )
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.lg.scaled(scale)),
                                    ) {
                                        val lastIngested =
                                            if (source.lastIngestedAtMs > 0) {
                                                DateText.dateTime(LocalContext.current, source.lastIngestedAtMs)
                                            } else {
                                                stringResource(R.string.epg_source_never)
                                            }

                                        val latestProgStr =
                                            if (latestTime > 0) {
                                                DateText.dateTime(LocalContext.current, latestTime * 1000L)
                                            } else {
                                                stringResource(R.string.epg_source_none)
                                            }

                                        SourceStat(stringResource(R.string.epg_source_stat_label), lastIngested, scale)
                                        if (wasUnchanged) {
                                            // The last run found no change and skipped parsing: the stored download/ingest times are from an
                                            // older run and would read as work done now.
                                            CinemaText(
                                                text = stringResource(R.string.epg_source_stat_unchanged),
                                                role = TextRole.Caption,
                                                tone = TextTone.Warning,
                                            )
                                        } else {
                                            SourceStat(
                                                stringResource(R.string.epg_source_stat_download),
                                                NumberUtils.formatDuration(LocalResources.current, source.lastDownloadDurationMs),
                                                scale,
                                            )
                                            SourceStat(
                                                stringResource(R.string.epg_source_stat_ingest),
                                                NumberUtils.formatDuration(LocalResources.current, source.lastIngestionDurationMs),
                                                scale,
                                            )
                                            SourceStat(
                                                stringResource(R.string.epg_source_stat_index),
                                                NumberUtils.formatDuration(LocalResources.current, source.lastIndexDurationMs),
                                                scale,
                                            )
                                        }
                                        SourceStat(stringResource(R.string.epg_source_stat_latest), latestProgStr, scale)
                                        SourceStat(
                                            stringResource(R.string.epg_source_stat_channels),
                                            NumberUtils.formatCount(LocalResources.current, source.lastChannels),
                                            scale,
                                        )
                                        SourceStat(
                                            stringResource(R.string.epg_source_stat_programmes),
                                            NumberUtils.formatCount(LocalResources.current, source.lastProgrammes),
                                            scale,
                                        )
                                    }

                                    // The last refresh failed: its time, the failures since the
                                    // last success and the reason, on a line of their own.
                                    val lastError = source.lastError
                                    if (lastError != null) {
                                        Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.lg.scaled(scale)),
                                        ) {
                                            source.lastFailedAtMs?.let { failedAt ->
                                                SourceStat(
                                                    stringResource(R.string.epg_source_stat_last_failure),
                                                    DateText.dateTime(LocalContext.current, failedAt),
                                                    scale,
                                                )
                                            }
                                            if (source.failuresSinceSuccess > 0) {
                                                SourceStat(
                                                    stringResource(R.string.epg_source_stat_failures),
                                                    NumberUtils.formatCount(LocalResources.current, source.failuresSinceSuccess),
                                                    scale,
                                                )
                                            }
                                            CinemaText(
                                                text = stringResource(R.string.epg_database_error, storedErrorText(lastError)),
                                                role = TextRole.Caption,
                                                tone = TextTone.Danger,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (withoutGuide.isNotEmpty()) {
                        item(key = "without-guide", contentType = "heading") {
                            SectionHeader(
                                title = stringResource(R.string.epg_sources_without_guide_title),
                                modifier = Modifier.padding(top = Spacing.sm.scaled(scale)),
                            )
                        }
                        // Each source with no guide source, with its own Add (that source checked in Use for).
                        items(withoutGuide, key = { "add-${it.id}" }, contentType = { "add" }) { provider ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                            ) {
                                TvIconAction(
                                    onClick = {
                                        addForSource = provider.id
                                        showAddDialog = true
                                    },
                                    icon = CinemaIcons.Add,
                                    label = stringResource(R.string.epg_add_source),
                                    modifier =
                                        (if (provider.id == providerId) Modifier.focusRequester(entryAddRowFocus) else Modifier)
                                            .leftToRail(),
                                )
                                CinemaText(text = provider.name, role = TextRole.Heading)
                            }
                        }
                    }
                    // The guide data of every source, as in Settings' Sources & guide (Settings reorg, Phase 3).
                    item {
                        GuideMaintenanceRow(
                            viewModel = viewModel,
                            onOpen = { showMaintenance = true },
                            focusRequester = maintenanceFocus,
                        )
                    }
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
            text = { CinemaText(stringResource(R.string.epg_cellular_confirm_message), tone = TextTone.Secondary) },
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
            scale = scale,
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
            scale = scale,
            forSources = guideProviders,
            initialFor = card.usedBy.mapTo(HashSet()) { it.id },
            ownGuideOf = card.ownGuideOf,
        )
    }

    clearingCard?.let { card ->
        CinemaAlertDialog(
            onDismissRequest = { clearingCard = null },
            danger =
                DialogAction(stringResource(R.string.epg_clear_source_data_button)) {
                    viewModel.clearSourceData(card.source.id)
                    clearingCard = null
                },
            cancel = DialogAction(stringResource(R.string.common_cancel)) { clearingCard = null },
            title = stringResource(R.string.epg_clear_source_data_title),
            text = {
                CinemaText(
                    stringResource(R.string.epg_clear_source_data_message, card.source.label.ifBlank { card.source.url }),
                    tone = TextTone.Secondary,
                )
            },
        )
    }

    deletingCard?.let { card ->
        val name = card.source.label.ifBlank { card.source.url }
        CinemaAlertDialog(
            onDismissRequest = { deletingCard = null },
            danger =
                DialogAction(stringResource(R.string.provider_delete_button)) {
                    viewModel.deleteSource(card.source.id)
                    deletingCard = null
                },
            cancel = DialogAction(stringResource(R.string.common_cancel)) { deletingCard = null },
            title = stringResource(R.string.epg_delete_source_confirm_title),
            text = {
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
                    tone = TextTone.Secondary,
                )
            },
        )
    }
}

/** The list index of card [index]: after the refresh row, when it shows. */
private fun cardIndex(
    index: Int,
    refreshRow: Boolean,
): Int = index + if (refreshRow) 1 else 0

/** The list index of the Add row [index] under Sources Without a Guide Source: after the [cardCount] cards and the heading. */
private fun addRowIndex(
    cardCount: Int,
    index: Int,
    refreshRow: Boolean,
): Int = cardIndex(cardCount, refreshRow) + 1 + index

/**
 * A card's lines on its use: "Used for: A, B", then "Not used by this profile" when the profile
 * in use has none of them; a source's own guide adds that its switch is "Provides a guide" (Edit
 * Source).
 */
@Composable
private fun GuideCardUse(card: EpgManagementViewModel.GuideCard) {
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
    if (card.ownGuideOf != null) {
        CinemaText(
            text = stringResource(R.string.epg_source_own_guide_hint),
            role = TextRole.BodySmall,
            tone = TextTone.Secondary,
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

@Composable
private fun StatusIndicator(
    source: EpgSourceEntity,
    nowMs: Long,
    staleThresholdMs: Long,
    scale: Float,
) {
    val color =
        when {
            !source.enabled -> CinemaTheme.colors.textPrimary.copy(alpha = CinemaAlpha.tint)
            source.lastError != null -> CinemaTheme.colors.danger
            source.lastIngestedAtMs == 0L -> CinemaTheme.colors.textPrimary.copy(alpha = CinemaAlpha.tint)
            nowMs - source.lastIngestedAtMs > staleThresholdMs -> CinemaTheme.colors.warning
            else -> CinemaTheme.colors.success
        }

    androidx.compose.foundation.Canvas(modifier = Modifier.size(TvDimensions.liveDotSize.scaled(scale))) {
        drawCircle(color = color)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SourceStat(
    label: String,
    value: String,
    scale: Float,
) {
    Column {
        CinemaText(text = label, role = TextRole.Caption, tone = TextTone.Secondary)
        CinemaText(text = value, role = TextRole.BodySmall)
    }
}

private val secretQueryValue = Regex("""([?&;](?:password|pass|pwd|token|api_key|api-key|access_token)=)[^&#]+""", RegexOption.IGNORE_CASE)

/**
 * A guide source's URL shortened to what tells same-label rows apart (M-10, as on mobile):
 * host[:port], then the last path segment and the query, with password-like values masked and
 * any user:pass@ dropped. Unparseable input comes back with the same masking.
 */
private fun guideSourceUrlHint(url: String): String {
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpgSourceEditDialog(
    initialSource: EpgSourceEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (url: String, label: String, tz: Int, method: String, enabled: Boolean, forSources: Set<Long>) -> Unit,
    scale: Float,
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
            // Scrolls: with several sources the Use for list runs past the dialog's height, and
            // rows cut off below took focus out of sight.
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                // The same text entry as every other form: a value row, OK opens the keyboard.
                // The first one takes the dialog's first focus (DialogFirstField).
                ReadOnlyFieldWithEdit(
                    value = url,
                    onValueChange = { url = it },
                    label = stringResource(R.string.epg_xmltv_url_label),
                    keyboardOptions = FieldKeyboards.Url,
                )
                FieldNote(stringResource(R.string.epg_xmltv_url_hint))
                ReadOnlyFieldWithEdit(
                    value = label,
                    onValueChange = { label = it },
                    label = stringResource(R.string.epg_label_optional),
                    keyboardOptions = FieldKeyboards.Name,
                )
                ReadOnlyFieldWithEdit(
                    value = tzOffset,
                    onValueChange = { tzOffset = it },
                    label = stringResource(R.string.epg_timezone_label),
                    keyboardOptions = FieldKeyboards.SignedNumber,
                )
                FieldNote(stringResource(R.string.epg_timezone_hint))
                if (ownGuideOf != null) {
                    CinemaText(
                        stringResource(R.string.epg_source_used_for, ownGuideOf.name),
                        role = TextRole.BodySmall,
                        tone = TextTone.Secondary,
                    )
                } else if (showUseFor) {
                    CinemaText(stringResource(R.string.epg_use_for_label), role = TextRole.Label, tone = TextTone.Secondary)
                    forSources.forEach { source ->
                        TvCheckRow(
                            checked = source.id in checkedFor,
                            onCheckedChange = { on -> checkedFor = if (on) checkedFor + source.id else checkedFor - source.id },
                            label = source.name,
                        )
                    }
                }
            }
        },
    )
}

/** A field's note under its row in a form (what to type, the usual value). */
@Composable
private fun FieldNote(text: String) {
    CinemaText(text, role = TextRole.BodySmall, tone = TextTone.Secondary, modifier = Modifier.padding(horizontal = Spacing.md))
}
