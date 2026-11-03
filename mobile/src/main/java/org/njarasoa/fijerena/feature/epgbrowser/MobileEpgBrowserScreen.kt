package org.njarasoa.fijerena.feature.epgbrowser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserAiring
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserMatchedStream
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserProgram
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.core.network.xmltv.EpgSearchPath
import org.njarasoa.fijerena.core.network.xmltv.GuideChannels
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.filterMatchedOnly
import org.njarasoa.fijerena.core.network.xmltv.filterToStreams
import org.njarasoa.fijerena.core.network.xmltv.freshnessLabel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.BadgeKind
import org.njarasoa.fijerena.core.ui.components.CinemaBadge
import org.njarasoa.fijerena.core.ui.components.CinemaCheckbox
import org.njarasoa.fijerena.core.ui.components.CinemaIcon
import org.njarasoa.fijerena.core.ui.components.CinemaProgressBar
import org.njarasoa.fijerena.core.ui.components.CinemaSpinner
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.MitadyLoading
import org.njarasoa.fijerena.core.ui.components.PanelFill
import org.njarasoa.fijerena.core.ui.components.SectionHeader
import org.njarasoa.fijerena.core.ui.components.SourceBadge
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.panel
import org.njarasoa.fijerena.core.ui.components.rememberNowEpochSeconds
import org.njarasoa.fijerena.core.ui.history.LocalNerd
import org.njarasoa.fijerena.core.ui.sports.WatchQuestionBody
import org.njarasoa.fijerena.core.ui.sports.programmeReminderOf
import org.njarasoa.fijerena.core.ui.sports.rememberGameBells
import org.njarasoa.fijerena.core.ui.sports.watchQuestionTitle
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.core.ui.utils.AppNotices
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.utils.canOpenCalendar
import org.njarasoa.fijerena.core.ui.utils.openAddToCalendarEvent
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.EpgBrowserViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.EpgBrowserViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.message
import org.njarasoa.fijerena.core.ui.viewmodels.noResultsMessage
import org.njarasoa.fijerena.core.ui.viewmodels.statsLine
import org.njarasoa.fijerena.navigation.LocalDockInset
import org.njarasoa.fijerena.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.ui.components.CinemaRow
import org.njarasoa.fijerena.ui.components.CinemaSearchField
import org.njarasoa.fijerena.ui.components.CinemaSwitchRow
import org.njarasoa.fijerena.ui.components.ClearSearchHistoryDialog
import org.njarasoa.fijerena.ui.components.MobileDayHeading
import org.njarasoa.fijerena.ui.components.MobileEmptyState
import org.njarasoa.fijerena.ui.components.MobileErrorState
import org.njarasoa.fijerena.ui.components.MobileLoadingState
import org.njarasoa.fijerena.ui.components.MobileScreenHeader
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.ui.components.buttons.IconAction
import org.njarasoa.fijerena.ui.components.cards.CinemaCard
import org.njarasoa.fijerena.ui.components.chips.CinemaAssistChip
import org.njarasoa.fijerena.ui.theme.CinemaBackground
import org.njarasoa.fijerena.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.ui.theme.CinemaWarning
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * "Search the guide". Opened from a TV Guide ([categoryId] set, GD5), an "In <category> only"
 * checkbox — on by default — keeps the results on that guide's channels. One search, programme
 * titles (D7). When the profile has a source with live channels the top bar has TV Guide (the
 * grid for Recent on the first of them, [onTvGuide]) and Guide sources (the one Guide Sources
 * page, [onGuideSources]) beside Refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileEpgBrowserScreen(
    onBack: () -> Unit,
    onNavigateToPlayer: (EpgBrowserMatchedStream) -> Unit = {},
    categoryId: String? = null,
    categoryName: String? = null,
    providerId: Long = 0L,
    onGuideSources: () -> Unit = {},
    onTvGuide: (categoryId: String, categoryName: String, providerId: Long) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    // An Expert's only: the Guide Sources button (the profile rules plan, PR-11).
    val expert =
        remember {
            org.njarasoa.fijerena.core.network
                .AppSettings(context)
                .isDevMode
        }
    val viewModel: EpgBrowserViewModel =
        viewModel(
            factory = remember { EpgBrowserViewModelFactory(context.applicationContext, categoryId, providerId) },
        )
    val contextName = categoryName?.takeIf { categoryId != null }
    val indexState by viewModel.indexState.collectAsStateWithLifecycle()
    val guideSourceOwners by viewModel.guideSourceOwners.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    val recentLabel = stringResource(R.string.category_recent_label)

    val staleSourceCount by viewModel.staleSourceCount.collectAsStateWithLifecycle()
    val processingState by viewModel.epgProcessingState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { AppNotices.show(it.asString(context)) }
    }

    val isRefreshing =
        processingState is EpgFileManager.MultiSourceState.Pending ||
            processingState is EpgFileManager.MultiSourceState.Processing ||
            processingState is EpgFileManager.MultiSourceState.Finalizing ||
            indexState is EpgIndexState.Indexing ||
            indexState is EpgIndexState.Optimizing

    Scaffold(
        topBar = {
            // No source subtitle: the search covers every source of the profile. The guide's
            // status goes below the bar.
            MobileScreenHeader(title = stringResource(R.string.epg_browser_title), onBack = onBack) {
                // The grid for Recent (D7); Back from it returns here.
                guideSourceOwners.firstOrNull()?.let { (firstLiveSource, _) ->
                    IconAction(
                        onClick = { onTvGuide(CategoryViewModel.RECENT_CATEGORY_ID, recentLabel, firstLiveSource) },
                        icon = CinemaIcons.DateRange,
                        label = stringResource(R.string.common_tv_guide),
                    )
                }
                if (isRefreshing) {
                    // Where the button was, at its size.
                    Box(modifier = Modifier.minimumInteractiveComponentSize(), contentAlignment = Alignment.Center) {
                        CinemaSpinner(modifier = Modifier.size(MobileDimensions.progressIndicatorSmall))
                    }
                } else {
                    IconAction(
                        onClick = { viewModel.refreshStale() },
                        icon = CinemaIcons.Refresh,
                        label = stringResource(R.string.epg_browser_refresh_stale_description),
                        tone = if (staleSourceCount > 0) TextTone.Warning else TextTone.Primary,
                    )
                }
                // The guides this search runs over, of a source that can have them: an Expert's
                // (the profile rules plan, PR-11).
                if (guideSourceOwners.isNotEmpty() && expert) {
                    IconAction(onClick = onGuideSources, icon = CinemaIcons.Tune, label = stringResource(R.string.epg_sources_header))
                }
            }
        },
        // Above the floating bottom bar, not behind it.
    ) { paddingValues ->
        MobileGuideSearchPane(
            viewModel = viewModel,
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onNavigateToPlayer = onNavigateToPlayer,
            contextName = contextName,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        )
    }
}

/**
 * The body of "Search the guide" under its top bar: the guide's status, the field, the
 * "In <category> only" checkbox ([contextName] set), the index progress, recent searches and
 * results. The host owns the field's text ([query], [onQueryChange]) and the snackbar for the
 * view model's messages.
 */
@Composable
internal fun MobileGuideSearchPane(
    viewModel: EpgBrowserViewModel,
    query: String,
    onQueryChange: (String) -> Unit,
    onNavigateToPlayer: (EpgBrowserMatchedStream) -> Unit,
    modifier: Modifier = Modifier,
    contextName: String? = null,
) {
    val context = LocalContext.current
    val contextChannels by viewModel.contextChannels.collectAsStateWithLifecycle()
    // Opened from a TV Guide: its channels only, until unticked (GD5).
    var inContextOnly by rememberSaveable { mutableStateOf(true) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val indexState by viewModel.indexState.collectAsStateWithLifecycle()
    val epgSearchHistory by viewModel.epgSearchHistory.collectAsStateWithLifecycle()

    val keyboardController = LocalSoftwareKeyboardController.current
    val nerd = LocalNerd.current
    val oldestIngestedAtMs by viewModel.oldestEnabledIngestedAtMs.collectAsStateWithLifecycle()
    val neverRunSourceCount by viewModel.neverRunSourceCount.collectAsStateWithLifecycle()
    val hasOffSources by viewModel.hasOffSources.collectAsStateWithLifecycle()
    val staleSourceCount by viewModel.staleSourceCount.collectAsStateWithLifecycle()
    val epgDbStats =
        when (val idx = indexState) {
            is EpgIndexState.Indexed -> {
                stringResource(
                    R.string.epg_browser_dev_stats_counts_format,
                    NumberUtils.formatCount(LocalResources.current, idx.programmeCount),
                    NumberUtils.formatCount(LocalResources.current, idx.channelCount),
                )
            }

            else -> {
                null
            }
        }

    // Unmatched airings (no channel in the profile's sources) stay hidden; the toggle is gone
    // until a later change gives it a home.
    val matchedOnly = true

    val nowEpoch = rememberNowEpochSeconds()

    Column(modifier = modifier) {
        // The guide's status, one line; the index counters only for a Nerd.
        val freshnessText =
            freshnessLabel(context, oldestIngestedAtMs, nowEpoch, staleSourceCount, neverRunSourceCount, hasOffSources)
        val freshnessColor =
            if (staleSourceCount > 0 || neverRunSourceCount > 0 || oldestIngestedAtMs == 0L ||
                (oldestIngestedAtMs == null && hasOffSources)
            ) {
                TextTone.Warning
            } else {
                TextTone.Secondary
            }
        CinemaText(
            text = freshnessText,
            role = TextRole.BodySmall,
            tone = freshnessColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Spacing.md),
        )
        if (nerd && epgDbStats != null) {
            CinemaText(
                text = stringResource(R.string.epg_browser_index_stats_format, epgDbStats),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Spacing.md),
                role = TextRole.BodySmall,
                tone = TextTone.Secondary,
            )
        }

        val placeholderText = stringResource(R.string.epg_browser_search_titles_placeholder)
        CinemaSearchField(
            value = query,
            onValueChange = { onQueryChange(it) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = CinemaSpacing.xs),
            placeholder = placeholderText,
            leadingIcon = {
                CinemaIcon(
                    imageVector = CinemaIcons.Search,
                    contentDescription = null,
                    modifier = Modifier.size(MobileDimensions.iconSmall),
                )
            },
            trailingIcon = {
                val hasResults = (uiState as? EpgBrowserViewModel.UiState.Results)?.totalPrograms ?: 0 > 0
                if (query.isNotEmpty() || hasResults) {
                    IconAction(
                        onClick = {
                            onQueryChange("")
                            viewModel.clearSearch()
                        },
                        icon = CinemaIcons.Close,
                        label = stringResource(R.string.provider_clear_button),
                        iconModifier = Modifier.size(MobileDimensions.iconSmall),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions =
                KeyboardActions(
                    onSearch = {
                        if (query.isNotBlank()) {
                            viewModel.performSearch(query)
                            keyboardController?.hide()
                        }
                    },
                ),
        )

        // "In <category> only" (GD5).
        if (contextName != null) {
            CinemaSwitchRow(
                checked = inContextOnly,
                onCheckedChange = { inContextOnly = it },
                label = stringResource(R.string.epg_browser_in_category_only_format, contextName),
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
        }

        val currentIndexState = indexState
        if (currentIndexState is EpgIndexState.Indexing || currentIndexState is EpgIndexState.Optimizing) {
            val idx = currentIndexState
            val progressText =
                if (idx is EpgIndexState.Indexing) {
                    stringResource(
                        R.string.epg_indexing_progress_count,
                        idx.progressPercent,
                        NumberUtils.formatCount(LocalResources.current, idx.programmesIndexed),
                    )
                } else {
                    stringResource(
                        R.string.epg_browser_finalizing_format,
                        NumberUtils.formatCount(LocalResources.current, (idx as EpgIndexState.Optimizing).programmeCount),
                    )
                }
            val progressValue = if (idx is EpgIndexState.Indexing) idx.progressPercent / 100f else 0.95f

            Column(
                modifier =
                    Modifier.padding(
                        horizontal = Spacing.md,
                        vertical = CinemaSpacing.xxs,
                    ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CinemaText(
                        text =
                            if (idx is EpgIndexState.Indexing) {
                                stringResource(
                                    R.string.epg_browser_indexing_label,
                                )
                            } else {
                                stringResource(R.string.epg_browser_optimizing_label)
                            },
                        role = TextRole.Caption,
                        tone = TextTone.Accent,
                    )
                    CinemaText(
                        text = progressText,
                        role = TextRole.Caption,
                        tone = TextTone.Secondary,
                    )
                }
                CinemaProgressBar(
                    progress = { progressValue },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(MobileDimensions.strokeWidth)
                            .padding(top = CinemaSpacing.xxxs),
                )
            }
        }

        when (val state = uiState) {
            is EpgBrowserViewModel.UiState.Idle -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (epgSearchHistory.isNotEmpty()) {
                        MobileEpgSearchHistorySection(
                            history = epgSearchHistory,
                            onItemClick = { term ->
                                onQueryChange(term)
                                viewModel.performSearch(term)
                                keyboardController?.hide()
                            },
                            onItemRemove = { viewModel.removeEpgSearchHistoryEntry(it) },
                            onClearAll = { viewModel.clearEpgSearchHistory() },
                        )
                    }
                    MobileEmptyState(message = stringResource(R.string.epg_browser_hint_search_titles), icon = CinemaIcons.Search)
                }
            }

            is EpgBrowserViewModel.UiState.IndexBusy -> {
                MobileLoadingState(message = state.message())
            }

            is EpgBrowserViewModel.UiState.NoEpgFile -> {
                MobileEmptyState(message = stringResource(R.string.epg_browser_no_file_message), icon = CinemaIcons.DateRange)
            }

            is EpgBrowserViewModel.UiState.Searching -> {
                MobileLoadingState()
            }

            is EpgBrowserViewModel.UiState.Results -> {
                MobileResultsContent(
                    nerd = nerd,
                    results = state,
                    nowEpoch = nowEpoch,
                    matchedOnly = matchedOnly,
                    onNavigateToPlayer = onNavigateToPlayer,
                    contextName = contextName.takeIf { inContextOnly },
                    contextChannels = contextChannels,
                    guideOutOfDate = staleSourceCount > 0,
                    onRefreshGuide = { viewModel.refreshStale() },
                )
            }

            is EpgBrowserViewModel.UiState.Error -> {
                MobileErrorState(message = state.message, onRetry = { viewModel.performSearch(query) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MobileResultsContent(
    results: EpgBrowserViewModel.UiState.Results,
    nowEpoch: Long,
    nerd: Boolean,
    matchedOnly: Boolean = true,
    onNavigateToPlayer: (EpgBrowserMatchedStream) -> Unit = {},
    // The TV Guide list the results are limited to (GD5), and its channels once loaded.
    contextName: String? = null,
    contextChannels: List<GuideChannels>? = null,
    // A guide source is out of date: a search with no results says so, with Refresh.
    guideOutOfDate: Boolean = false,
    onRefreshGuide: () -> Unit = {},
) {
    // Filter date groups when hiding unmatched channels, then to the guide's channels when asked
    val contextFilter = contextChannels?.takeIf { contextName != null }
    val displayDateGroups =
        remember(results.dateGroups, matchedOnly, contextFilter) {
            val matched = if (matchedOnly) filterMatchedOnly(results.dateGroups) else results.dateGroups
            contextFilter?.let { filterToStreams(matched, it) } ?: matched
        }

    Column {
        // Counts, timing and search path: information for a Nerd, as on TV.
        if (nerd) {
            CinemaText(
                text = results.statsLine(),
                modifier =
                    Modifier.padding(
                        horizontal = Spacing.md,
                        vertical = CinemaSpacing.xxs,
                    ),
                role = TextRole.Body,
                tone = TextTone.Secondary,
            )
        }
        if (results.searchPath == EpgSearchPath.LIKE_FALLBACK) {
            CinemaText(
                text = stringResource(R.string.epg_browser_partial_results_note),
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = CinemaSpacing.xxs),
                role = TextRole.BodySmall,
                tone = TextTone.Warning,
            )
        }

        if (displayDateGroups.isEmpty()) {
            // An out-of-date guide may simply not have the programme yet: say so, with Refresh.
            MobileEmptyState(
                message = results.noResultsMessage(matchedOnly, contextName.takeIf { contextFilter != null }),
                icon = CinemaIcons.Search,
                secondary = if (guideOutOfDate) stringResource(R.string.epg_search_no_results_stale) else null,
                actionLabel = if (guideOutOfDate) stringResource(R.string.common_refresh) else null,
                onAction = if (guideOutOfDate) onRefreshGuide else null,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = Spacing.md),
                contentPadding = PaddingValues(bottom = LocalDockInset.current),
            ) {
                displayDateGroups.forEach { dateGroup ->
                    stickyHeader(key = "date::${dateGroup.dateLabel}::${dateGroup.dayStartEpoch}::$matchedOnly", contentType = "header") {
                        MobileDayHeading(text = dateGroup.dateLabel, pinned = true)
                    }
                    items(
                        dateGroup.programs,
                        key = { it.id },
                        contentType = { "program" },
                    ) { program ->
                        MobileProgramCard(
                            program = program,
                            nowEpoch = nowEpoch,
                            onNavigateToPlayer = onNavigateToPlayer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileProgramCard(
    program: EpgBrowserProgram,
    nowEpoch: Long,
    onNavigateToPlayer: (EpgBrowserMatchedStream) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val showExpander = program.airings.size > 3
    var pendingConfirmAiring by remember { mutableStateOf<EpgBrowserAiring?>(null) }
    // Out of the dialog: closing it must not cancel the reminder it sets.
    val bells = rememberGameBells()

    CinemaCard(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(CinemaSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CinemaText(
                    text = program.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    role = TextRole.Heading,
                    tone = TextTone.Inherit,
                )
                val category = program.category
                if (category != null) {
                    CinemaText(
                        text = category,
                        modifier = Modifier.padding(start = CinemaSpacing.sm),
                        role = TextRole.Caption,
                        tone = TextTone.Secondary,
                    )
                }
            }

            val description = program.description
            if (!description.isNullOrBlank()) {
                CinemaText(
                    text = description,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = CinemaSpacing.xs),
                    role = TextRole.BodySmall,
                    tone = TextTone.Secondary,
                )
            }

            Spacer(modifier = Modifier.height(CinemaSpacing.xs))
            val visibleAirings =
                if (expanded || !showExpander) {
                    program.airings
                } else {
                    program.airings.take(3)
                }
            visibleAirings.forEach { airing ->
                MobileAiringRow(
                    airing = airing,
                    nowEpoch = nowEpoch,
                    onNavigateToPlayer = onNavigateToPlayer,
                    onRequestConfirmation = { pendingConfirmAiring = it },
                )
            }

            val pending = pendingConfirmAiring
            val matched = pending?.matchedStream
            if (pending != null && matched != null) {
                val airingContext = LocalContext.current
                val reminder = programmeReminderOf(program.title, pending, nowEpoch)?.takeUnless { bells.reminded.hasProgramme(it) }
                CinemaAlertDialog(
                    onDismissRequest = { pendingConfirmAiring = null },
                    title = watchQuestionTitle(pending.startEpoch),
                    text = { WatchQuestionBody(pending.channelName, matched.providerId) },
                    main =
                        DialogAction(stringResource(R.string.epg_browser_watch_now_btn)) {
                            // A second click before the dialog leaves doesn't open the player twice (R-20).
                            if (pendingConfirmAiring != null) {
                                pendingConfirmAiring = null
                                onNavigateToPlayer(matched)
                            }
                        },
                    others =
                        listOfNotNull(
                            reminder?.let {
                                DialogAction(stringResource(R.string.sports_remind_me)) {
                                    pendingConfirmAiring = null
                                    bells.remindProgramme(it)
                                }
                            },
                            if (pending.startEpoch > nowEpoch && canOpenCalendar(airingContext)) {
                                DialogAction(stringResource(R.string.epg_browser_add_calendar_btn)) {
                                    pendingConfirmAiring = null
                                    openAddToCalendarEvent(
                                        context = airingContext,
                                        title = program.title,
                                        description = program.description,
                                        location = pending.channelName,
                                        startEpochSeconds = pending.startEpoch,
                                        endEpochSeconds = pending.endEpoch,
                                    )
                                }
                            } else {
                                null
                            },
                        ),
                    cancel = DialogAction(stringResource(R.string.common_cancel)) { pendingConfirmAiring = null },
                )
            }

            if (showExpander) {
                CinemaRow(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(top = CinemaSpacing.xs),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CinemaIcon(
                        imageVector = if (expanded) CinemaIcons.ExpandLess else CinemaIcons.ExpandMore,
                        contentDescription =
                            if (expanded) {
                                stringResource(
                                    R.string.epg_browser_show_less,
                                )
                            } else {
                                stringResource(R.string.epg_browser_show_more)
                            },
                        modifier = Modifier.size(CinemaSpacing.lg),
                        tone = TextTone.Accent,
                    )
                    CinemaText(
                        text =
                            if (expanded) {
                                stringResource(R.string.epg_browser_show_less)
                            } else {
                                pluralStringResource(
                                    R.plurals.epg_browser_show_more_count,
                                    program.airings.size - 3,
                                    program.airings.size - 3,
                                )
                            },
                        role = TextRole.Caption,
                        tone = TextTone.Accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileAiringRow(
    airing: EpgBrowserAiring,
    nowEpoch: Long,
    onNavigateToPlayer: (EpgBrowserMatchedStream) -> Unit = {},
    onRequestConfirmation: (EpgBrowserAiring) -> Unit = {},
) {
    val isOnAir = nowEpoch >= airing.startEpoch && nowEpoch < airing.endEpoch
    val isSoon = !isOnAir && airing.startEpoch > nowEpoch && (airing.startEpoch - nowEpoch) <= 7200L
    val matched = airing.matchedStream
    val isMatched = matched != null

    val onClick =
        remember(matched, isOnAir, airing, onNavigateToPlayer, onRequestConfirmation) {
            if (matched == null) {
                null
            } else {
                {
                    if (isOnAir) {
                        onNavigateToPlayer(matched)
                    } else {
                        onRequestConfirmation(airing)
                    }
                }
            }
        }

    CinemaRow(
        onClick = onClick,
        modifier = Modifier.alpha(if (isMatched) 1f else 0.5f),
        contentPadding = PaddingValues(vertical = CinemaSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
    ) {
        if (isMatched) {
            CinemaIcon(
                imageVector = CinemaIcons.PlayArrow,
                contentDescription = stringResource(R.string.epg_browser_watch_now_btn),
                modifier = Modifier.size(CinemaSpacing.lg),
                tone = if (isOnAir) TextTone.Success else TextTone.Accent,
            )
        }
        CinemaText(
            text = airing.channelName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            role = TextRole.Heading,
            tone = TextTone.Primary,
        )
        // After the name, as on every other row; lighter than the card it sits on.
        airing.sourceName?.let { SourceBadge(it, kind = BadgeKind.OnCard) }
        if (isOnAir || isSoon) {
            val badgeLabel =
                if (isOnAir) {
                    stringResource(
                        R.string.epg_browser_on_air_badge,
                    )
                } else {
                    stringResource(R.string.epg_browser_soon_badge)
                }
            CinemaBadge(
                text = badgeLabel,
                kind = if (isOnAir) BadgeKind.Success else BadgeKind.Warning,
            )
        }
        CinemaText(
            text = TimeFormat.formatTimeRange(airing.startEpoch, airing.endEpoch),
            role = TextRole.BodySmall,
            tone =
                if (isOnAir) {
                    TextTone.Success
                } else if (isSoon) {
                    TextTone.Warning
                } else {
                    TextTone.Secondary
                },
        )
    }
}

@Composable
private fun MobileEpgSearchHistorySection(
    history: List<String>,
    onItemClick: (String) -> Unit,
    onItemRemove: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    // Clearing every recent search asks first, as Clear Recent does.
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) {
        ClearSearchHistoryDialog(
            onConfirm = {
                confirmClear = false
                onClearAll()
            },
            onDismiss = { confirmClear = false },
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SectionHeader(
            title = stringResource(R.string.epg_browser_recent_searches),
            trailing = {
                IconAction(
                    onClick = { confirmClear = true },
                    icon = CinemaIcons.Delete,
                    label = stringResource(R.string.epg_browser_clear_all_description),
                    tone = TextTone.Danger,
                    iconModifier = Modifier.size(MobileDimensions.iconSmall),
                )
            },
        )
        // Wraps rather than scrolling sideways: the whole history should be visible at a glance.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            history.forEach { term ->
                CinemaAssistChip(
                    onClick = { onItemClick(term) },
                    label = {
                        CinemaText(
                            term,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            role = TextRole.Label,
                            keepCase = true,
                            tone = TextTone.Inherit,
                        )
                    },
                    leadingIcon = {
                        CinemaIcon(
                            imageVector = CinemaIcons.Search,
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                    trailingIcon = {
                        IconAction(
                            onClick = { onItemRemove(term) },
                            icon = CinemaIcons.Close,
                            label = stringResource(R.string.epg_browser_remove_description),
                            buttonSize = AssistChipDefaults.IconSize,
                            iconModifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                )
            }
        }
    }
}
