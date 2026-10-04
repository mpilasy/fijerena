package org.njarasoa.fijerena.feature.epg

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.ToggleableSurfaceDefaults
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager.MultiSourceState
import org.njarasoa.fijerena.core.network.xtream.manager.AutoXmltvSources
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.provider.components.ProviderDangerButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.modifiers.tvDpadEscape
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * One source's guide sources (A-9, T6): "Guide sources · <source>", the list under its bulk
 * actions, and an empty state that offers to add one. Guide auto-refresh and maintenance are
 * device-wide and live in Settings (Source & guide, Backup & storage).
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
                    SettingsViewModelFactory(context.applicationContext, providerId = providerId)
                },
        )

    // null until the first emission, so the empty state doesn't flash (or take focus) while the
    // list loads.
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

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { message ->
            android.widget.Toast
                .makeText(context, message.asString(context), android.widget.Toast.LENGTH_SHORT)
                .show()
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<EpgSourceEntity?>(null) }
    var deletingSource by remember { mutableStateOf<EpgSourceEntity?>(null) }
    var deleteSelectedIds by remember { mutableStateOf<Set<Long>?>(null) }

    val scale = LocalUiScale.current
    val sourceList = sources.orEmpty()
    val firstSourceId = sourceList.firstOrNull()?.id

    // Entry focus, once the list has loaded: the first guide source, or Add guide source when
    // there is none (the TV focus contract, plan Part I, B).
    val firstRowFocus = remember { FocusRequester() }
    val addFocus = remember { FocusRequester() }
    val loaded = sources != null
    LaunchedEffect(loaded) {
        if (loaded) (if (firstSourceId != null) firstRowFocus else addFocus).requestFocusWithRetry()
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
            Text(
                text =
                    providerName?.let { stringResource(R.string.epg_management_title_format, it) }
                        ?: stringResource(R.string.epg_sources_header),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))

            LazyColumn(
                contentPadding = PaddingValues(vertical = Spacing.xs.scaled(scale)),
                verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                modifier = Modifier.fillMaxSize().focusRestorer(),
            ) {
                if (sources?.isEmpty() == true) {
                    // Empty state: one line and the one thing to do here.
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg.scaled(scale)),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                        ) {
                            Text(
                                stringResource(R.string.epg_summary_no_sources),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
                            )
                            CinemaPrimaryButton(
                                onClick = { showAddDialog = true },
                                text = stringResource(R.string.epg_add_source),
                                modifier = Modifier.focusRequester(addFocus),
                            )
                        }
                    }
                } else if (sources != null) {
                    // Add, and the bulk actions over this list.
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                        ) {
                            CinemaPrimaryButton(
                                onClick = { showAddDialog = true },
                                text = stringResource(R.string.epg_add_source),
                                modifier = Modifier.focusRequester(addFocus),
                            )

                            if (selectedIds.isNotEmpty()) {
                                CinemaSecondaryButton(
                                    onClick = {
                                        viewModel.refreshSelected(selectedIds)
                                        viewModel.clearSelection()
                                    },
                                    text = stringResource(R.string.epg_refresh_selected_btn, selectedIds.size),
                                )

                                CinemaDangerButton(
                                    onClick = { deleteSelectedIds = selectedIds },
                                    text = stringResource(R.string.epg_delete_selected_btn, selectedIds.size),
                                )
                            }

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

                items(sourceList, key = { it.id }, contentType = { "source" }) { source ->
                    val isSelected = selectedIds.contains(source.id)
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
                                androidx.tv.material3.Surface(
                                    checked = isSelected,
                                    onCheckedChange = { viewModel.toggleSelection(source.id) },
                                    modifier =
                                        if (source.id == firstSourceId) Modifier.focusRequester(firstRowFocus) else Modifier,
                                    // P5: checked keeps the resting container and shows the
                                    // check glyph in the current colour; only focus lifts it.
                                    colors =
                                        ToggleableSurfaceDefaults.colors(
                                            containerColor = TvFocusTokens.restingContainer,
                                            contentColor = CinemaTextSecondary,
                                            focusedContainerColor = TvFocusTokens.focusedContainer,
                                            focusedContentColor = CinemaTextPrimary,
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
                                                    border = BorderStroke(TvFocusTokens.focusBorderWidth, CinemaAccentLight),
                                                    shape = CircleShape,
                                                ),
                                        ),
                                    glow = ToggleableSurfaceDefaults.glow(focusedGlow = TvFocusTokens.focusedGlow),
                                    shape = ToggleableSurfaceDefaults.shape(shape = CircleShape),
                                ) {
                                    Box(
                                        modifier = Modifier.size(TvDimensions.iconLarge.scaled(scale)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) CinemaIcons.CheckCircle else CinemaIcons.RadioButtonUnchecked,
                                            contentDescription =
                                                if (isSelected) {
                                                    stringResource(
                                                        R.string.epg_source_selected_description,
                                                    )
                                                } else {
                                                    stringResource(R.string.epg_source_not_selected_description)
                                                },
                                            modifier = Modifier.size(Spacing.lg.scaled(scale)),
                                        )
                                    }
                                }

                                StatusIndicator(source, nowMs, viewModel.staleThresholdMs(source), scale)

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = source.label.ifBlank { stringResource(R.string.epg_unnamed_source) },
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = guideSourceUrlHint(source.url),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    // The source's own guide, disabled by "Provides a guide" (Edit Source).
                                    if (!source.enabled && provider?.let { AutoXmltvSources.isAutoXmltvSource(source, it.url) } == true) {
                                        Text(
                                            text = stringResource(R.string.epg_source_own_guide_off),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
                                CinemaSecondaryButton(
                                    onClick = { viewModel.refreshSource(source.id) },
                                    text = stringResource(R.string.common_refresh),
                                )
                                CinemaSecondaryButton(
                                    onClick = { editingSource = source },
                                    text = stringResource(R.string.provider_edit_button),
                                )
                                ProviderDangerButton(
                                    onClick = { deletingSource = source },
                                    text = stringResource(R.string.provider_delete_button),
                                )
                            }

                            if (activeProgress != null) {
                                Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs.scaled(scale))) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text =
                                                stringResource(
                                                    R.string.epg_source_phase_format,
                                                    localizedEpgPhase(activeProgress.phase),
                                                ),
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
                                        modifier = Modifier.fillMaxWidth().height(Spacing.xxs.scaled(scale)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                    )
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
                                        Text(
                                            text = stringResource(R.string.epg_source_stat_unchanged),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = org.njarasoa.fijerena.ui.theme.CinemaWarning,
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
                                        Text(
                                            text = stringResource(R.string.epg_database_error, lastError),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
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

    if (showAddDialog) {
        EpgSourceEditDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { url, label, tz, method, enabled ->
                viewModel.addSource(url, label, tz, method, enabled)
                showAddDialog = false
            },
            scale = scale,
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
                Text(
                    stringResource(R.string.epg_delete_source_confirm_title),
                    color = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary,
                )
            },
            text = {
                Text(
                    stringResource(
                        R.string.epg_delete_source_message_tv,
                        source.label.ifBlank {
                            source.url
                        },
                    ),
                    color = org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary,
                )
            },
            containerColor = org.njarasoa.fijerena.core.ui.theme.CinemaSurface,
        )
    }

    deleteSelectedIds?.let { idsToDelete ->
        CinemaAlertDialog(
            onDismissRequest = { deleteSelectedIds = null },
            confirmButton = {
                CinemaDangerButton(
                    onClick = {
                        viewModel.deleteSelected(idsToDelete)
                        deleteSelectedIds = null
                    },
                    text = stringResource(R.string.epg_delete_sources_count_btn, idsToDelete.size),
                )
            },
            dismissButton = {
                CinemaSecondaryButton(
                    onClick = { deleteSelectedIds = null },
                    text = stringResource(R.string.common_cancel),
                )
            },
            title = {
                Text(
                    stringResource(R.string.epg_delete_selected_confirm_title),
                    color = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary,
                )
            },
            text = {
                Text(
                    stringResource(R.string.epg_delete_selected_confirm_message, idsToDelete.size),
                    color = org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary,
                )
            },
            containerColor = org.njarasoa.fijerena.core.ui.theme.CinemaSurface,
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

@Composable
private fun StatusIndicator(
    source: EpgSourceEntity,
    nowMs: Long,
    staleThresholdMs: Long,
    scale: Float,
) {
    val color =
        when {
            !source.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            source.lastError != null -> MaterialTheme.colorScheme.error
            source.lastIngestedAtMs == 0L -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            nowMs - source.lastIngestedAtMs > staleThresholdMs -> org.njarasoa.fijerena.ui.theme.CinemaWarning
            else -> org.njarasoa.fijerena.ui.theme.CinemaSuccess
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
    onConfirm: (url: String, label: String, tz: Int, method: String, enabled: Boolean) -> Unit,
    scale: Float,
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
                color = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary,
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
                    )
                },
                text = stringResource(R.string.provider_save_button),
                enabled = url.isNotBlank(),
            )
        },
        dismissButton = {
            CinemaSecondaryButton(onClick = onDismiss, text = stringResource(R.string.common_cancel))
        },
        containerColor = org.njarasoa.fijerena.core.ui.theme.CinemaSurface,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
                androidx.compose.material3.OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.epg_xmltv_url_label)) },
                    modifier = Modifier.fillMaxWidth().tvDpadEscape(),
                    singleLine = true,
                    colors =
                        androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary,
                            unfocusedTextColor = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary,
                            cursorColor = org.njarasoa.fijerena.core.ui.theme.CinemaAccent,
                            focusedBorderColor = org.njarasoa.fijerena.core.ui.theme.CinemaAccent,
                            unfocusedBorderColor = org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant,
                            focusedLabelColor = org.njarasoa.fijerena.core.ui.theme.CinemaAccent,
                            unfocusedLabelColor = org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary,
                        ),
                )
                androidx.compose.material3.OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.epg_label_optional)) },
                    modifier = Modifier.fillMaxWidth().tvDpadEscape(),
                    singleLine = true,
                    colors =
                        androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary,
                            unfocusedTextColor = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary,
                            cursorColor = org.njarasoa.fijerena.core.ui.theme.CinemaAccent,
                            focusedBorderColor = org.njarasoa.fijerena.core.ui.theme.CinemaAccent,
                            unfocusedBorderColor = org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant,
                            focusedLabelColor = org.njarasoa.fijerena.core.ui.theme.CinemaAccent,
                            unfocusedLabelColor = org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary,
                        ),
                )
                androidx.compose.material3.OutlinedTextField(
                    value = tzOffset,
                    onValueChange = { tzOffset = it },
                    label = { Text(stringResource(R.string.epg_timezone_label)) },
                    modifier = Modifier.fillMaxWidth().tvDpadEscape(),
                    singleLine = true,
                    colors =
                        androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary,
                            unfocusedTextColor = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary,
                            cursorColor = org.njarasoa.fijerena.core.ui.theme.CinemaAccent,
                            focusedBorderColor = org.njarasoa.fijerena.core.ui.theme.CinemaAccent,
                            unfocusedBorderColor = org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant,
                            focusedLabelColor = org.njarasoa.fijerena.core.ui.theme.CinemaAccent,
                            unfocusedLabelColor = org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary,
                        ),
                )
            }
        },
    )
}
