package org.njarasoa.fijerena.feature.epg

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.ToggleableSurfaceDefaults
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
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.provider.components.ProviderDangerButton
import org.njarasoa.fijerena.feature.settings.components.GuideMaintenancePane
import org.njarasoa.fijerena.feature.settings.components.GuideMaintenanceRow
import org.njarasoa.fijerena.feature.settings.components.PickerOption
import org.njarasoa.fijerena.feature.settings.components.SettingsPickerPane
import org.njarasoa.fijerena.ui.components.TvEmptyState
import org.njarasoa.fijerena.ui.components.TvScreenHeader
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction
import org.njarasoa.fijerena.ui.components.input.TvCheckRow
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.modifiers.tvDpadEscape
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * One source's guide sources (A-9, T6): "Guide sources · <source>", the list under its bulk
 * actions, and an empty state that offers to add one. Each row shows its own auto-refresh, and
 * its Auto-refresh button drills into a picker in place of the list (P5b). Guide maintenance is
 * device-wide: its row is at the foot of this list and in Settings (Sources & guide).
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
    // list loads. One group per source with channels (the one guide sources page plan).
    val groups by viewModel.groups.collectAsStateWithLifecycle(initialValue = null)
    val sources = groups?.flatMap { it.sources }
    val latestProgrammeTimes by viewModel.latestProgrammeTimes.collectAsStateWithLifecycle()
    val staleSourceCount by viewModel.staleSourceCount.collectAsStateWithLifecycle()
    val failedSourceCount by viewModel.failedSourceCount.collectAsStateWithLifecycle()
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()

    val nowMs = remember { System.currentTimeMillis() }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { message ->
            android.widget.Toast
                .makeText(context, message.asString(context), android.widget.Toast.LENGTH_SHORT)
                .show()
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<EpgSourceEntity?>(null) }
    var deletingSource by remember { mutableStateOf<EpgSourceEntity?>(null) }

    val scale = LocalUiScale.current
    val listState = rememberLazyListState()
    val groupList = groups.orEmpty()
    // The group the page was opened on ([providerId]), if it is one of them.
    val openGroup = groupList.firstOrNull { it.provider.id == providerId }

    // Entry focus, once the list has loaded: the first guide source of the group the page was
    // opened on (scrolled into view), else the first guide source; Add guide source when that group
    // has none. TvEmptyState focuses its action itself (the TV focus contract, plan Part I, B).
    val entryRowFocus = remember { FocusRequester() }
    val addFocus = remember { FocusRequester() }
    var entrySourceId by remember { mutableStateOf<Long?>(null) }
    val loaded = groups != null
    LaunchedEffect(loaded) {
        if (!loaded || sources.isNullOrEmpty()) return@LaunchedEffect
        val target = if (openGroup != null) openGroup.sources.firstOrNull() else sources.first()
        if (target == null) {
            addFocus.requestFocusWithRetry()
        } else {
            entrySourceId = target.id
            openGroup?.let { listState.scrollToItem(groupHeaderIndex(groupList, it.provider.id)) }
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
            TvScreenHeader(title = stringResource(R.string.epg_management_screen_title))

            // No guide source: one sentence and the one thing to do here, centred (TV UI audit #20).
            // The action takes focus itself; Left from it goes to the navigation rail (leftToRail
            // here reaches the action, the only focusable inside).
            if (sources?.isEmpty() == true) {
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
                    if (sources != null) {
                        // Add, and refreshing the stale or failed sources of this list.
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                            ) {
                                CinemaPrimaryButton(
                                    onClick = { showAddDialog = true },
                                    text = stringResource(R.string.epg_add_source),
                                    modifier = Modifier.focusRequester(addFocus).leftToRail(),
                                )

                                if (staleSourceCount > 0) {
                                    CinemaSecondaryButton(
                                        onClick = { viewModel.refreshStale() },
                                        text = stringResource(R.string.epg_refresh_stale_btn, staleSourceCount),
                                    )
                                }

                                if (failedSourceCount > 0) {
                                    CinemaSecondaryButton(
                                        onClick = { viewModel.refreshFailed() },
                                        text = stringResource(R.string.epg_retry_failed_btn, failedSourceCount),
                                    )
                                }
                            }
                        }
                    }

                    groupList.forEach { group ->
                        item(key = "group-${group.provider.id}", contentType = "group") { GuideGroupHeader(group, scale) }
                        if (group.sources.isEmpty()) {
                            item(key = "none-${group.provider.id}", contentType = "none") {
                                CinemaText(
                                    text = stringResource(R.string.epg_summary_no_sources),
                                    role = TextRole.BodySmall,
                                    tone = TextTone.Secondary,
                                )
                            }
                        }
                        items(group.sources, key = { it.id }, contentType = { "source" }) { source ->
                            val latestTime = latestProgrammeTimes[source.id] ?: 0L

                            val activeProgress =
                                if (processingState is MultiSourceState.Processing) {
                                    (processingState as MultiSourceState.Processing).activeProgress[source.id]
                                } else {
                                    null
                                }

                            // Whether the run that just finished confirmed this source's data hadn't
                            // changed (304, or a matching content hash) — true only right after that run,
                            // resets once the next processing state replaces this one.
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
                                        androidx.tv.material3.Surface(
                                            checked = source.enabled,
                                            onCheckedChange = { viewModel.setSourceEnabled(source, it) },
                                            modifier =
                                                (if (source.id == entrySourceId) Modifier.focusRequester(entryRowFocus) else Modifier)
                                                    .leftToRail()
                                                    .semantics { contentDescription = useLabel },
                                            // P5: checked keeps the resting container and shows the
                                            // check glyph in the current colour; only focus lifts it.
                                            colors =
                                                ToggleableSurfaceDefaults.colors(
                                                    containerColor = TvFocusTokens.restingContainer,
                                                    contentColor = CinemaTheme.colors.textSecondary,
                                                    focusedContainerColor = TvFocusTokens.focusedContainer,
                                                    focusedContentColor = CinemaTheme.colors.textPrimary,
                                                    selectedContainerColor = TvFocusTokens.restingContainer,
                                                    selectedContentColor = TvFocusTokens.currentText,
                                                    focusedSelectedContainerColor = TvFocusTokens.focusedContainer,
                                                    focusedSelectedContentColor = TvFocusTokens.currentText,
                                                ),
                                            scale =
                                                ToggleableSurfaceDefaults.scale(
                                                    scale = TvFocusTokens.defaultScale,
                                                    focusedScale = TvFocusTokens.focusedScale,
                                                    selectedScale = TvFocusTokens.defaultScale,
                                                    pressedScale = TvFocusTokens.pressedScale,
                                                ),
                                            border =
                                                ToggleableSurfaceDefaults.border(
                                                    border = Border.None,
                                                    focusedBorder =
                                                        Border(
                                                            border =
                                                                BorderStroke(
                                                                    TvFocusTokens.focusBorderWidth,
                                                                    CinemaTheme.colors.accentSoft,
                                                                ),
                                                            shape = CinemaTheme.shapes.control,
                                                        ),
                                                ),
                                            glow = ToggleableSurfaceDefaults.glow(focusedGlow = TvFocusTokens.focusedGlow),
                                            shape = ToggleableSurfaceDefaults.shape(shape = CinemaTheme.shapes.control),
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(Spacing.xs.scaled(scale)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                CinemaSwitch(
                                                    checked = source.enabled,
                                                    onCheckedChange = null,
                                                )
                                            }
                                        }

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
                                            // The source's own guide, whose switch is "Provides a guide" (Edit Source).
                                            if (AutoXmltvSources.isAutoXmltvSource(source, group.provider.url)) {
                                                CinemaText(
                                                    text = stringResource(R.string.epg_source_own_guide_hint),
                                                    role = TextRole.BodySmall,
                                                    tone = TextTone.Secondary,
                                                )
                                            }
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
                                            onClick = { editingSource = source },
                                            icon = CinemaIcons.Edit,
                                            label = stringResource(R.string.provider_edit_button),
                                        )
                                        TvIconAction(
                                            onClick = { intervalSource = source },
                                            icon = CinemaIcons.Sync,
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
                                        TvIconAction(
                                            onClick = { deletingSource = source },
                                            icon = CinemaIcons.Delete,
                                            label = stringResource(R.string.provider_delete_button),
                                            danger = true,
                                        )
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
                                                            NumberUtils.formatBytes(activeProgress.downloadedBytes)
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
                                                    java.text
                                                        .SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                                                        .format(java.util.Date(source.lastIngestedAtMs))
                                                } else {
                                                    stringResource(R.string.epg_source_never)
                                                }

                                            val latestProgStr =
                                                if (latestTime > 0) {
                                                    java.text
                                                        .SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                                                        .format(java.util.Date(latestTime * 1000L))
                                                } else {
                                                    stringResource(R.string.epg_source_none)
                                                }

                                            SourceStat(stringResource(R.string.epg_source_stat_label), lastIngested, scale)
                                            if (wasUnchanged) {
                                                // The last run confirmed no change and skipped parsing —
                                                // the stored download/ingest durations are from whenever
                                                // it last actually ran, so showing them here would read
                                                // as work that didn't happen this time.
                                                CinemaText(
                                                    text = stringResource(R.string.epg_source_stat_unchanged),
                                                    role = TextRole.Caption,
                                                    tone = TextTone.Warning,
                                                )
                                            } else {
                                                SourceStat(
                                                    stringResource(R.string.epg_source_stat_download),
                                                    NumberUtils.formatDuration(source.lastDownloadDurationMs),
                                                    scale,
                                                )
                                                SourceStat(
                                                    stringResource(R.string.epg_source_stat_ingest),
                                                    NumberUtils.formatDuration(source.lastIngestionDurationMs),
                                                    scale,
                                                )
                                            }
                                            SourceStat(stringResource(R.string.epg_source_stat_latest), latestProgStr, scale)
                                            SourceStat(
                                                stringResource(R.string.epg_source_stat_channels),
                                                NumberUtils.formatCount(source.lastChannels),
                                                scale,
                                            )
                                            SourceStat(
                                                stringResource(R.string.epg_source_stat_programmes),
                                                NumberUtils.formatCount(source.lastProgrammes),
                                                scale,
                                            )

                                            val lastError = source.lastError
                                            if (lastError != null) {
                                                CinemaText(
                                                    text = stringResource(R.string.epg_database_error, lastError),
                                                    role = TextRole.Caption,
                                                    tone = TextTone.Danger,
                                                )
                                            }
                                        }
                                    }
                                }
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

    if (showAddDialog) {
        EpgSourceEditDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { url, label, tz, method, enabled, forSources ->
                viewModel.addSource(url, label, tz, forSources, method, enabled)
                showAddDialog = false
            },
            scale = scale,
            // Use for (GS-D1): the group the page was opened on, else the first.
            forSources = groupList.map { it.provider },
            initialFor = setOfNotNull((openGroup ?: groupList.firstOrNull())?.provider?.id),
        )
    }

    editingSource?.let { source ->
        EpgSourceEditDialog(
            initialSource = source,
            onDismiss = { editingSource = null },
            onConfirm = { url, label, tz, method, enabled, _ ->
                viewModel.updateSource(
                    source.copy(url = url, label = label, timezoneOffsetHours = tz, ingestMethod = method, enabled = enabled),
                )
                editingSource = null
            },
            scale = scale,
        )
    }

    deletingSource?.let { source ->
        CinemaAlertDialog(
            onDismissRequest = { deletingSource = null },
            confirmButton = {
                CinemaDangerButton(
                    onClick = {
                        viewModel.deleteSource(source.id)
                        deletingSource = null
                    },
                    text = stringResource(R.string.provider_delete_button),
                )
            },
            dismissButton = {
                CinemaSecondaryButton(
                    onClick = { deletingSource = null },
                    text = stringResource(R.string.common_cancel),
                )
            },
            title = {
                CinemaText(stringResource(R.string.epg_delete_source_confirm_title), role = TextRole.Title)
            },
            text = {
                CinemaText(
                    stringResource(
                        R.string.epg_delete_source_message_tv,
                        source.label.ifBlank {
                            source.url
                        },
                    ),
                    tone = TextTone.Secondary,
                )
            },
            containerColor = CinemaTheme.colors.surface,
        )
    }
}

/** The list index of [providerId]'s group header: after the buttons row, each group's header, then its rows (or its "none" line). */
private fun groupHeaderIndex(
    groups: List<EpgManagementViewModel.GuideGroup>,
    providerId: Long,
): Int {
    var index = 1
    for (group in groups) {
        if (group.provider.id == providerId) return index
        index += 1 + maxOf(group.sources.size, 1)
    }
    return 0
}

/** A group's header: the source's name, and "Not used by this profile" when the profile in use doesn't have it. */
@Composable
private fun GuideGroupHeader(
    group: EpgManagementViewModel.GuideGroup,
    scale: Float,
) {
    Column(modifier = Modifier.padding(top = Spacing.sm.scaled(scale))) {
        CinemaText(text = group.provider.name, role = TextRole.Title)
        if (!group.usedByProfile) {
            CinemaText(
                text = stringResource(R.string.provider_not_in_profile),
                role = TextRole.BodySmall,
                tone = TextTone.Secondary,
            )
        }
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
    // Adding: the sources the guide source can be for; the list shows with two or more (GS-D1).
    forSources: List<ProviderEntity> = emptyList(),
    initialFor: Set<Long> = emptySet(),
) {
    var url by remember { mutableStateOf(initialSource?.url ?: "") }
    var checkedFor by remember { mutableStateOf(initialFor) }
    var label by remember { mutableStateOf(initialSource?.label ?: "") }
    var tzOffset by remember { mutableStateOf(initialSource?.timezoneOffsetHours?.toString() ?: "0") }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            CinemaText(
                if (initialSource == null) {
                    stringResource(R.string.epg_add_source_title)
                } else {
                    stringResource(R.string.epg_edit_source_title)
                },
                role = TextRole.Title,
            )
        },
        confirmButton = {
            CinemaPrimaryButton(
                onClick = {
                    onConfirm(
                        url,
                        label,
                        tzOffset.toIntOrNull() ?: 0,
                        initialSource?.ingestMethod ?: "DOWNLOADED",
                        initialSource?.enabled ?: true,
                        checkedFor,
                    )
                },
                text = stringResource(R.string.provider_save_button),
                enabled = url.isNotBlank() && (initialSource != null || checkedFor.isNotEmpty()),
            )
        },
        dismissButton = {
            CinemaSecondaryButton(onClick = onDismiss, text = stringResource(R.string.common_cancel))
        },
        containerColor = CinemaTheme.colors.surface,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
                CinemaTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = stringResource(R.string.epg_xmltv_url_label),
                    keyboardOptions = FieldKeyboards.Url,
                    supportingText = stringResource(R.string.epg_xmltv_url_hint),
                    modifier = Modifier.fillMaxWidth().tvDpadEscape(),
                    singleLine = true,
                )
                CinemaTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = stringResource(R.string.epg_label_optional),
                    keyboardOptions = FieldKeyboards.Name,
                    modifier = Modifier.fillMaxWidth().tvDpadEscape(),
                    singleLine = true,
                )
                CinemaTextField(
                    value = tzOffset,
                    onValueChange = { tzOffset = it },
                    label = stringResource(R.string.epg_timezone_label),
                    keyboardOptions = FieldKeyboards.SignedNumber,
                    supportingText = stringResource(R.string.epg_timezone_hint),
                    modifier = Modifier.fillMaxWidth().tvDpadEscape(),
                    singleLine = true,
                )
                if (initialSource == null && forSources.size > 1) {
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
