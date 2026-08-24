@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.epgbrowser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserAiring
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserProgram
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.core.network.xmltv.filterMatchedOnly
import org.njarasoa.fijerena.core.network.xmltv.formatAiringTime
import org.njarasoa.fijerena.core.network.xmltv.formatCount
import org.njarasoa.fijerena.core.network.xmltv.formatFileSize
import org.njarasoa.fijerena.core.network.xmltv.freshnessLabel
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.bounceMarquee
import org.njarasoa.fijerena.core.ui.components.rememberNowEpochSeconds
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaBackground
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceLight
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaWarning
import org.njarasoa.fijerena.core.ui.viewmodels.EpgBrowserViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.EpgBrowserViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.noResultsMessage
import org.njarasoa.fijerena.core.ui.viewmodels.statsLine
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.components.input.TvSelectableButton
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.scaled
import org.njarasoa.fijerena.core.ui.components.MitadyLoading
import org.njarasoa.fijerena.ui.components.TvSearchTextField
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.R

@Composable
fun TvEpgBrowserScreen(
    onBack: () -> Unit,
    onNavigateToPlayer: (streamId: String, streamName: String, categoryId: String) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    val viewModel: EpgBrowserViewModel =
        viewModel(
            factory = remember { EpgBrowserViewModelFactory(context.applicationContext) },
        )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val indexState by viewModel.indexState.collectAsStateWithLifecycle()
    val searchMode by viewModel.searchMode.collectAsStateWithLifecycle()
    val activeProviderName by viewModel.activeProviderName.collectAsStateWithLifecycle()
    val isDevMode = viewModel.isDevMode
    val sourceLabels by viewModel.sourceLabels.collectAsStateWithLifecycle()
    val epgSearchHistory by viewModel.epgSearchHistory.collectAsStateWithLifecycle()
    val oldestIngestedAtMs by viewModel.oldestEnabledIngestedAtMs.collectAsStateWithLifecycle()
    val neverRunSourceCount by viewModel.neverRunSourceCount.collectAsStateWithLifecycle()
    val staleSourceCount by viewModel.staleSourceCount.collectAsStateWithLifecycle()
    val processingState by viewModel.epgProcessingState.collectAsStateWithLifecycle()
    val epgDbStats =
        when (val idx = indexState) {
            is EpgIndexState.Indexed -> stringResource(R.string.epg_browser_dev_stats_counts_format, formatCount(idx.programmeCount), formatCount(idx.channelCount))
            else -> null
        }
    val isRefreshing =
        processingState is EpgFileManager.MultiSourceState.Pending ||
            processingState is EpgFileManager.MultiSourceState.Processing ||
            processingState is EpgFileManager.MultiSourceState.Finalizing

    val nowEpoch = rememberNowEpochSeconds()
    val scale = LocalUiScale.current

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = Spacing.tvSafeMarginHorizontal,
                        vertical = Spacing.tvSafeMarginVertical,
                    ),
        ) {
                // Header
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.epg_browser_title),
                        style =
                            MaterialTheme.typography.displaySmall.copy(
                                fontSize =
                                    MaterialTheme.typography.displaySmall.fontSize
                                        .scaled(scale),
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val providerName = activeProviderName
                    if (providerName != null) {
                        Text(
                            text = stringResource(R.string.epg_browser_provider_suffix_format, providerName),
                            style =
                                MaterialTheme.typography.titleLarge.copy(
                                    fontSize =
                                        MaterialTheme.typography.titleLarge.fontSize
                                            .scaled(scale),
                                ),
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                            modifier = Modifier.padding(bottom = Spacing.xs.scaled(scale), start = Spacing.xs.scaled(scale)),
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Freshness + refresh button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
                        modifier = Modifier.padding(bottom = Spacing.xs.scaled(scale)),
                    ) {
                        val freshnessText =
                            freshnessLabel(context, oldestIngestedAtMs, nowEpoch, staleSourceCount, neverRunSourceCount)
                        val freshnessColor =
                            if (staleSourceCount > 0 || neverRunSourceCount > 0 || oldestIngestedAtMs == 0L) {
                                CinemaWarning
                            } else {
                                CinemaTextSecondary
                            }
                        Text(
                            text = freshnessText,
                            style =
                                MaterialTheme.typography.labelLarge.copy(
                                    fontSize =
                                        MaterialTheme.typography.labelLarge.fontSize
                                            .scaled(scale),
                                ),
                            color = freshnessColor,
                        )
                        CinemaIconButton(
                            onClick = { if (!isRefreshing) viewModel.refreshStale() },
                            icon = {
                                if (isRefreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(TvDimensions.iconMedium.scaled(scale)),
                                        color = CinemaAccent,
                                        strokeWidth = TvDimensions.borderFocused,
                                    )
                                } else {
                                    Icon(
                                        imageVector = CinemaIcons.Refresh,
                                        contentDescription = stringResource(R.string.epg_browser_refresh_stale_description),
                                        tint = if (staleSourceCount > 0) CinemaWarning else CinemaTextPrimary,
                                        modifier = Modifier.size(TvDimensions.iconMedium.scaled(scale)),
                                    )
                                }
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                when (val state = uiState) {
                    is EpgBrowserViewModel.UiState.NoEpgFile -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.epg_browser_no_file_message),
                                style =
                                    MaterialTheme.typography.bodyLarge.copy(
                                        fontSize =
                                            MaterialTheme.typography.bodyLarge.fontSize
                                                .scaled(scale),
                                    ),
                                color = CinemaTextSecondary,
                            )
                        }
                    }
                    is EpgBrowserViewModel.UiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = state.message,
                                style =
                                    MaterialTheme.typography.bodyLarge.copy(
                                        fontSize =
                                            MaterialTheme.typography.bodyLarge.fontSize
                                                .scaled(scale),
                                    ),
                                color = CinemaError,
                            )
                        }
                    }
                    else -> {
                        EpgBrowserContent(
                            uiState = state,
                            nowEpoch = nowEpoch,
                            indexState = indexState,
                            isDevMode = isDevMode,
                            epgDbStats = epgDbStats,
                            sourceLabels = sourceLabels,
                            searchMode = searchMode,
                            epgSearchHistory = epgSearchHistory,
                            onSearchModeChange = { viewModel.setSearchMode(it) },
                            onSearch = { viewModel.performSearch(it) },
                            onRemoveHistoryEntry = { viewModel.removeEpgSearchHistoryEntry(it) },
                            onClearHistory = { viewModel.clearEpgSearchHistory() },
                            onClearSearch = { viewModel.clearSearch() },
                            onNavigateToPlayer = onNavigateToPlayer,
                        )
                    }
                }
        }
    }
}

@Composable
private fun EpgBrowserContent(
    uiState: EpgBrowserViewModel.UiState,
    nowEpoch: Long,
    indexState: EpgIndexState,
    isDevMode: Boolean,
    epgDbStats: String?,
    sourceLabels: Map<Long, String>,
    searchMode: EpgBrowserViewModel.SearchMode,
    epgSearchHistory: List<String> = emptyList(),
    onSearchModeChange: (EpgBrowserViewModel.SearchMode) -> Unit,
    onSearch: (String) -> Unit,
    onRemoveHistoryEntry: (String) -> Unit = {},
    onClearHistory: () -> Unit = {},
    onClearSearch: () -> Unit = {},
    onNavigateToPlayer: (String, String, String) -> Unit = { _, _, _ -> },
) {
    val searchFocusRequester = remember { FocusRequester() }
    val firstItemFocusRequester = remember { FocusRequester() }

    // Down from the search field should reach the first recent-search chip. Left to a spatial
    // focus search it lands on the "Recent Searches" header's clear-all button instead, which sits
    // between the field and the chips. Only wired up while the history is actually on screen —
    // aiming `down` at an unattached requester would stop Down working at all.
    val historyFocusRequester = remember { FocusRequester() }
    var localQuery by remember { mutableStateOf("") }
    var matchedOnly by remember { mutableStateOf(true) }
    val scale = LocalUiScale.current
    val hasResults = (uiState as? EpgBrowserViewModel.UiState.Results)?.totalPrograms ?: 0 > 0

    // The history chips only render in the Idle state, so that is the only time `down` has a
    // target to aim at.
    val showsHistory = uiState is EpgBrowserViewModel.UiState.Idle && epgSearchHistory.isNotEmpty()

    // Auto-focus logic: when results appear for the first time for a new query, focus the first item
    LaunchedEffect(uiState) {
        if (uiState is EpgBrowserViewModel.UiState.Results) {
            try {
                firstItemFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(searchMode) {
        localQuery = ""
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search field
        GlassPanel {
            Column(modifier = Modifier.padding(Spacing.sm.scaled(scale))) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                ) {
                    // Bare Material3 RadioButton/Checkbox draw focus through LocalIndication, which
                    // is a ripple — invisible without a pointer, so on TV these had no focus state
                    // at all. They are 2-way and 1-way pickers respectively, so the selectable
                    // button carries both states properly.
                    EpgBrowserViewModel.SearchMode.entries.forEach { mode ->
                        TvSelectableButton(
                            selected = searchMode == mode,
                            onSelect = { onSearchModeChange(mode) },
                            text =
                                when (mode) {
                                    EpgBrowserViewModel.SearchMode.PROGRAMME -> stringResource(R.string.epg_browser_search_mode_programme)
                                    EpgBrowserViewModel.SearchMode.CHANNEL -> stringResource(R.string.epg_browser_search_mode_channel)
                                },
                            modifier = Modifier.padding(end = Spacing.sm.scaled(scale)),
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    TvSelectableButton(
                        selected = matchedOnly,
                        onSelect = { matchedOnly = !matchedOnly },
                        text = stringResource(R.string.epg_browser_matched_only_label),
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))

                TvSearchTextField(
                    modifier =
                        if (showsHistory) {
                            Modifier.focusProperties { down = historyFocusRequester }
                        } else {
                            Modifier
                        },
                    query = localQuery,
                    onQueryChange = { localQuery = it },
                    onSearchSubmit = { onSearch(localQuery) },
                    onClear = {
                        localQuery = ""
                        onClearSearch()
                    },
                    placeholder = when (searchMode) {
                        EpgBrowserViewModel.SearchMode.PROGRAMME -> stringResource(R.string.epg_browser_enter_programme_placeholder)
                        EpgBrowserViewModel.SearchMode.CHANNEL -> stringResource(R.string.epg_browser_enter_channel_placeholder)
                    },
                    focusRequester = searchFocusRequester,
                    showClearButton = localQuery.isNotEmpty() || hasResults,
                )
            }
        }

        // Auto-focus on screen open
        LaunchedEffect(Unit) {
            try {
                searchFocusRequester.requestFocus()
            } catch (_: IllegalStateException) {
            }
        }

        // Dev mode: show EPG DB stats
        if (isDevMode && epgDbStats != null) {
            Text(
                text = stringResource(R.string.epg_browser_dev_stats_format, epgDbStats),
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontSize =
                            MaterialTheme.typography.labelSmall.fontSize
                                .scaled(scale),
                    ),
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textLow),
                modifier = Modifier.padding(top = Spacing.xs.scaled(scale)),
            )
        }

        // Indexing progress banner
        val currentIndexState = indexState
        if (currentIndexState is EpgIndexState.Indexing || currentIndexState is EpgIndexState.Optimizing) {
            val idx = currentIndexState
            val progressText = if (idx is EpgIndexState.Indexing) {
                stringResource(R.string.epg_browser_indexing_progress_programmes_format, idx.progressPercent, formatCount(idx.programmesIndexed))
            } else {
                stringResource(R.string.epg_browser_finalizing_programmes_format, formatCount((idx as EpgIndexState.Optimizing).programmeCount))
            }
            val progressValue = if (idx is EpgIndexState.Indexing) idx.progressPercent / 100f else 0.95f

            Column(modifier = Modifier.padding(top = Spacing.sm.scaled(scale))) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (idx is EpgIndexState.Indexing) stringResource(R.string.epg_browser_building_index_label) else stringResource(R.string.epg_browser_optimizing_index_label),
                        style =
                            MaterialTheme.typography.labelMedium.copy(
                                fontSize =
                                    MaterialTheme.typography.labelMedium.fontSize
                                        .scaled(scale),
                            ),
                        color = CinemaAccentLight,
                    )
                    Text(
                        text = progressText,
                        style =
                            MaterialTheme.typography.labelMedium.copy(
                                fontSize =
                                    MaterialTheme.typography.labelMedium.fontSize
                                        .scaled(scale),
                            ),
                        color = CinemaTextSecondary,
                    )
                }
                LinearProgressIndicator(
                    progress = { progressValue },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.xxs.scaled(scale)),
                    color = CinemaAccent,
                    trackColor = CinemaSurface,
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))

        when (uiState) {
            is EpgBrowserViewModel.UiState.Idle -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (epgSearchHistory.isNotEmpty()) {
                        EpgSearchHistorySection(
                            history = epgSearchHistory,
                            onItemClick = { term ->
                                localQuery = term
                                onSearch(term)
                            },
                            onItemRemove = onRemoveHistoryEntry,
                            onClearAll = onClearHistory,
                            firstItemFocusRequester = historyFocusRequester,
                        )
                        Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))
                    }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val hintText =
                            when (searchMode) {
                                EpgBrowserViewModel.SearchMode.PROGRAMME -> stringResource(R.string.epg_browser_hint_search_titles_local)
                                EpgBrowserViewModel.SearchMode.CHANNEL -> stringResource(R.string.epg_browser_hint_search_channels_6h)
                            }
                        Text(
                            text = hintText,
                            style =
                                MaterialTheme.typography.bodyLarge.copy(
                                    fontSize =
                                        MaterialTheme.typography.bodyLarge.fontSize
                                            .scaled(scale),
                                ),
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        )
                    }
                }
            }
            is EpgBrowserViewModel.UiState.Searching -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    MitadyLoading(
                        style = MaterialTheme.typography.headlineMedium,
                        color = CinemaAccent,
                    )
                }
            }
            is EpgBrowserViewModel.UiState.Results -> {
                ResultsContent(
                    results = uiState,
                    nowEpoch = nowEpoch,
                    isDevMode = isDevMode,
                    sourceLabels = sourceLabels,
                    searchMode = searchMode,
                    matchedOnly = matchedOnly,
                    onNavigateToPlayer = onNavigateToPlayer,
                    firstItemFocusRequester = firstItemFocusRequester,
                )
            }
            else -> {} // NoEpgFile and Error handled in parent
        }
    }
}

@Composable
private fun EpgSearchHistorySection(
    history: List<String>,
    onItemClick: (String) -> Unit,
    onItemRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null,
) {
    val scale = LocalUiScale.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.epg_browser_recent_searches),
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontSize =
                            MaterialTheme.typography.titleMedium.fontSize
                                .scaled(scale),
                    ),
                color = CinemaTextSecondary,
            )
            CinemaIconButton(
                onClick = onClearAll,
                icon = {
                    Icon(
                        imageVector = CinemaIcons.Delete,
                        contentDescription = stringResource(R.string.epg_browser_clear_all_description),
                        modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
                        tint = CinemaTextPrimary
                    )
                },
            )
        }
        // Wraps rather than scrolling sideways: the whole history should be visible at a glance,
        // and a D-pad user should not have to scroll a rail to reach the last entry.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
        ) {
            history.forEachIndexed { index, term ->
                Card(
                    onClick = { onItemClick(term) },
                    modifier =
                        if (index == 0 && firstItemFocusRequester != null) {
                            Modifier.focusRequester(firstItemFocusRequester)
                        } else {
                            Modifier
                        },
                    colors =
                        CardDefaults.colors(
                            containerColor = CinemaSurface,
                            focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.glassBorder),
                        ),
                    scale =
                        CardDefaults.scale(
                            scale = TvFocusTokens.defaultScale,
                            focusedScale = TvFocusTokens.focusedScaleContent,
                        ),
                    shape =
                        CardDefaults.shape(
                            shape = RoundedCornerShape(CornerRadius.medium),
                        ),
                ) {
                    Row(
                        modifier =
                            Modifier.padding(
                                horizontal = Spacing.md.scaled(scale),
                                vertical = Spacing.sm.scaled(scale),
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
                    ) {
                        Icon(
                            imageVector = CinemaIcons.Search,
                            contentDescription = null,
                            tint = CinemaTextSecondary,
                            modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
                        )
                        Text(
                            text = term,
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontSize =
                                        MaterialTheme.typography.bodyMedium.fontSize
                                            .scaled(scale),
                                ),
                            color = CinemaTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultsContent(
    results: EpgBrowserViewModel.UiState.Results,
    nowEpoch: Long,
    isDevMode: Boolean = false,
    sourceLabels: Map<Long, String> = emptyMap(),
    searchMode: EpgBrowserViewModel.SearchMode = EpgBrowserViewModel.SearchMode.PROGRAMME,
    matchedOnly: Boolean = true,
    onNavigateToPlayer: (String, String, String) -> Unit = { _, _, _ -> },
    firstItemFocusRequester: FocusRequester? = null,
) {
    val scale = LocalUiScale.current

    // Filter date groups when hiding unmatched channels
    val displayDateGroups =
        remember(results.dateGroups, matchedOnly) {
            if (matchedOnly) filterMatchedOnly(results.dateGroups) else results.dateGroups
        }

    Column {
        // Stats row
        Text(
            text = results.statsLine(),
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontSize =
                        MaterialTheme.typography.titleMedium.fontSize
                            .scaled(scale),
                ),
            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
            modifier = Modifier.padding(bottom = Spacing.sm.scaled(scale)),
        )

        if (displayDateGroups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = results.noResultsMessage(matchedOnly),
                    style =
                        MaterialTheme.typography.bodyLarge.copy(
                            fontSize =
                                MaterialTheme.typography.bodyLarge.fontSize
                                    .scaled(scale),
                        ),
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                )
            }
        } else {
            // Build index-to-dateLabel mapping for sticky header
            val headerIndices =
                remember(displayDateGroups) {
                    val indices = mutableListOf<Pair<Int, String>>()
                    var idx = 0
                    displayDateGroups.forEach { group ->
                        indices.add(idx to group.dateLabel)
                        idx++ // header item
                        idx += group.programs.size
                    }
                    indices
                }
            val listState = rememberTvLazyListState()
            val pinnedHeaderLabel by remember {
                derivedStateOf {
                    val firstVisible = listState.firstVisibleItemIndex
                    headerIndices.lastOrNull { it.first <= firstVisible }?.second
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                TvLazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    var isFirstItem = true
                    displayDateGroups.forEach { dateGroup ->
                        item(key = "date::${dateGroup.dateLabel}::${dateGroup.dayStartEpoch}::$matchedOnly", contentType = "header") {
                            DateHeader(dateLabel = dateGroup.dateLabel)
                        }
                        itemsIndexed(
                            dateGroup.programs,
                            key = { _, it -> it.id },
                            contentType = { _, _ -> "program" },
                        ) { index, program ->
                            ProgramCard(
                                program = program,
                                nowEpoch = nowEpoch,
                                isDevMode = isDevMode,
                                sourceLabels = sourceLabels,
                                onNavigateToPlayer = onNavigateToPlayer,
                                modifier = if (isFirstItem && index == 0) {
                                    isFirstItem = false
                                    if (firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                                } else Modifier
                            )
                        }
                    }
                }

                // Pinned sticky header overlay
                pinnedHeaderLabel?.let { label ->
                    DateHeader(
                        dateLabel = label,
                        modifier = Modifier.background(CinemaSurface),
                    )
                }
            }
        }
    }
}

@Composable
private fun DateHeader(
    dateLabel: String,
    modifier: Modifier = Modifier,
) {
    val scale = LocalUiScale.current
    Text(
        text = dateLabel,
        style =
            MaterialTheme.typography.titleSmall.copy(
                fontSize =
                    MaterialTheme.typography.titleSmall.fontSize
                        .scaled(scale),
            ),
        color = CinemaAccentLight,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xs.scaled(scale)),
    )
}

@Composable
private fun ProgramCard(
    program: EpgBrowserProgram,
    nowEpoch: Long,
    isDevMode: Boolean = false,
    sourceLabels: Map<Long, String> = emptyMap(),
    onNavigateToPlayer: (String, String, String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    val scale = LocalUiScale.current
    var pendingConfirmAiring by remember { mutableStateOf<EpgBrowserAiring?>(null) }

    androidx.compose.material3.Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xs.scaled(scale)),
        colors =
            androidx.compose.material3.CardDefaults.cardColors(
                containerColor = CinemaSurface,
                contentColor = CinemaTextPrimary,
            ),
        shape = RoundedCornerShape(CornerRadius.medium),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md.scaled(scale)),
        ) {
            // Title + category badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = program.title,
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontSize =
                                MaterialTheme.typography.titleMedium.fontSize
                                    .scaled(scale),
                        ),
                    color = CinemaTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).bounceMarquee(),
                )
                val category = program.category
                if (category != null) {
                    GlassPanel(
                        modifier = Modifier.padding(start = Spacing.sm.scaled(scale)),
                    ) {
                        Text(
                            text = category,
                            style =
                                MaterialTheme.typography.labelMedium.copy(
                                    fontSize =
                                        MaterialTheme.typography.labelMedium.fontSize
                                            .scaled(scale),
                                ),
                            color = CinemaAccentLight,
                            modifier =
                                Modifier.padding(
                                    horizontal = Spacing.sm.scaled(scale),
                                    vertical = Spacing.xxs.scaled(scale),
                                ),
                        )
                    }
                }
            }

            // Description (truncated)
            val description = program.description
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontSize =
                                MaterialTheme.typography.bodyMedium.fontSize
                                    .scaled(scale),
                        ),
                    color = CinemaTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.xs.scaled(scale)),
                )
            }

            // Airings
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
            program.airings.forEach { airing ->
                AiringRow(
                    airing = airing,
                    nowEpoch = nowEpoch,
                    isDevMode = isDevMode,
                    sourceLabels = sourceLabels,
                    onNavigateToPlayer = onNavigateToPlayer,
                    onRequestConfirmation = { pendingConfirmAiring = it },
                )
            }

            // Confirmation dialog for non-ON-AIR matched airings
            val pending = pendingConfirmAiring
            if (pending != null) {
                val matched = pending.matchedStream!!
                val airingContext = LocalContext.current
                CinemaAlertDialog(
                    onDismissRequest = { pendingConfirmAiring = null },
                    title = { Text(stringResource(R.string.epg_browser_watch_confirm_title), color = CinemaTextPrimary) },
                    text = {
                        Text(
                            stringResource(
                                R.string.epg_browser_watch_confirm_message,
                                formatAiringTime(airingContext, pending.startEpoch, pending.endEpoch),
                                pending.channelName,
                            ),
                            color = CinemaTextSecondary
                        )
                    },
                    confirmButton = {
                        CinemaButton(onClick = {
                            pendingConfirmAiring = null
                            onNavigateToPlayer(matched.streamId.toString(), matched.streamName, matched.categoryId)
                        }) { Text(stringResource(R.string.epg_browser_watch_now_btn)) }
                    },
                    dismissButton = {
                        CinemaButton(onClick = { pendingConfirmAiring = null }) { Text(stringResource(R.string.common_cancel)) }
                    },
                    containerColor = CinemaSurface,
                )
            }
        }
    }
}

@Composable
private fun AiringRow(
    airing: EpgBrowserAiring,
    nowEpoch: Long,
    isDevMode: Boolean = false,
    sourceLabels: Map<Long, String> = emptyMap(),
    onNavigateToPlayer: (String, String, String) -> Unit = { _, _, _ -> },
    onRequestConfirmation: (EpgBrowserAiring) -> Unit = {},
) {
    val isOnAir = nowEpoch >= airing.startEpoch && nowEpoch < airing.endEpoch
    val isSoon = !isOnAir && airing.startEpoch > nowEpoch && (airing.startEpoch - nowEpoch) <= 7200L
    val scale = LocalUiScale.current
    val isMatched = airing.matchedStream != null

    val rowContent: @Composable () -> Unit = {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .alpha(if (isMatched) 1f else 0.5f)
                    .padding(vertical = Spacing.xxs.scaled(scale)),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isMatched) {
                Icon(
                    imageVector = CinemaIcons.PlayArrow,
                    contentDescription = stringResource(R.string.epg_browser_watch_description),
                    modifier = Modifier.size(Spacing.lg.scaled(scale)),
                    tint = if (isOnAir) CinemaSuccess else CinemaAccentLight,
                )
            }
            Text(
                text = airing.channelName,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontSize =
                            MaterialTheme.typography.bodyMedium.fontSize
                                .scaled(scale),
                    ),
                color = CinemaAccentLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).bounceMarquee(),
            )
            if (isDevMode && airing.sourceId > 0) {
                val sourceName = sourceLabels[airing.sourceId]
                if (sourceName != null) {
                    Text(
                        text = sourceName,
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontSize =
                                    MaterialTheme.typography.labelSmall.fontSize
                                        .scaled(scale),
                            ),
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textLow),
                    )
                }
            }
            if (isOnAir || isSoon) {
                val badgeColor = if (isOnAir) CinemaSuccess else CinemaWarning
                val badgeLabel = if (isOnAir) stringResource(R.string.epg_browser_on_air_badge) else stringResource(R.string.epg_browser_soon_badge)
                Text(
                    text = badgeLabel,
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontSize =
                                MaterialTheme.typography.labelSmall.fontSize
                                    .scaled(scale),
                        ),
                    color = CinemaBackground,
                    modifier =
                        Modifier
                            .background(badgeColor, RoundedCornerShape(CornerRadius.small))
                            .padding(horizontal = Spacing.xs.scaled(scale), vertical = Spacing.xxs.scaled(scale)),
                )
            }
            val airingContext = LocalContext.current
            Text(
                text = formatAiringTime(airingContext, airing.startEpoch, airing.endEpoch),
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontSize =
                            MaterialTheme.typography.bodyMedium.fontSize
                                .scaled(scale),
                    ),
                color =
                    if (isOnAir) {
                        CinemaSuccess
                    } else if (isSoon) {
                        CinemaWarning
                    } else {
                        CinemaTextSecondary
                    },
            )
        }
    } // end rowContent

    Surface(
        onClick = {
            if (isMatched) {
                val matched = airing.matchedStream!!
                if (isOnAir) {
                    onNavigateToPlayer(matched.streamId.toString(), matched.streamName, matched.categoryId)
                } else {
                    onRequestConfirmation(airing)
                }
            }
        },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        rowContent()
    }
}

