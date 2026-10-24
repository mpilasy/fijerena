@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.epgbrowser

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserAiring
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserMatchedStream
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserProgram
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.core.network.xmltv.EpgSearchPath
import org.njarasoa.fijerena.core.network.xmltv.GuideChannels
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.filterMatchedOnly
import org.njarasoa.fijerena.core.network.xmltv.filterToStreams
import org.njarasoa.fijerena.core.network.xmltv.formatAiringTime
import org.njarasoa.fijerena.core.network.xmltv.formatCount
import org.njarasoa.fijerena.core.network.xmltv.formatFileSize
import org.njarasoa.fijerena.core.network.xmltv.freshnessLabel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.BadgeKind
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaProgressBar
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.MitadyLoading
import org.njarasoa.fijerena.core.ui.components.SourceBadge
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.bounceMarquee
import org.njarasoa.fijerena.core.ui.components.rememberNowEpochSeconds
import org.njarasoa.fijerena.core.ui.home.SourceChoice
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaBackground
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceLight
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.CinemaWarning
import org.njarasoa.fijerena.core.ui.utils.canOpenCalendar
import org.njarasoa.fijerena.core.ui.utils.openAddToCalendarEvent
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.EpgBrowserViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.EpgBrowserViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.message
import org.njarasoa.fijerena.core.ui.viewmodels.noResultsMessage
import org.njarasoa.fijerena.core.ui.viewmodels.statsLine
import org.njarasoa.fijerena.ui.components.TvScreenHeader
import org.njarasoa.fijerena.ui.components.TvSourceChooserDialog
import org.njarasoa.fijerena.ui.components.TvStatusBadge
import org.njarasoa.fijerena.ui.components.TvStatusBadgeKind
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction
import org.njarasoa.fijerena.ui.components.cards.TvCard
import org.njarasoa.fijerena.ui.components.cards.TvCardLook
import org.njarasoa.fijerena.ui.components.cards.TvCardLooks
import org.njarasoa.fijerena.ui.components.cards.TvRowTitle
import org.njarasoa.fijerena.ui.components.input.NavReturnFocus
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.TvSearchField
import org.njarasoa.fijerena.ui.components.input.TvSelectableButton
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled

// Header buttons that navigate away, for rememberNavReturnFocus.
private const val RETURN_GUIDE_SOURCES = "header:guideSources"
private const val RETURN_TV_GUIDE = "header:tvGuide"

/**
 * "Search the guide". Opened from a TV Guide ([categoryId] set, GD5), an "In <category> only"
 * toggle — on by default — keeps the results on that guide's channels. One search, programme
 * titles (D7: the channel mode is gone). When the profile has a source with live channels the
 * header has TV Guide, opening the grid for Recent on the first of them ([onTvGuide], D7), and
 * Guide sources next to Refresh, opening a source's guide sources ([onGuideSources]) — which
 * source, asked first when there are several (multi-source plan, O4).
 */
@Composable
fun TvEpgBrowserScreen(
    onBack: () -> Unit,
    onNavigateToPlayer: (EpgBrowserMatchedStream) -> Unit = {},
    categoryId: String? = null,
    categoryName: String? = null,
    providerId: Long = 0L,
    onGuideSources: (providerId: Long) -> Unit = {},
    onTvGuide: (categoryId: String, categoryName: String, providerId: Long) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    val viewModel: EpgBrowserViewModel =
        viewModel(
            factory = remember { EpgBrowserViewModelFactory(context.applicationContext, categoryId, providerId) },
        )
    val contextName = categoryName?.takeIf { categoryId != null }
    var query by remember { mutableStateOf("") }
    val indexState by viewModel.indexState.collectAsStateWithLifecycle()
    val guideSourceOwners by viewModel.guideSourceOwners.collectAsStateWithLifecycle()
    // Back from a header button's screen lands on that button (the results' own hand-back is
    // ResultsContent's).
    val headerReturnFocus = rememberNavReturnFocus()
    NavReturnFocusEffect(headerReturnFocus)
    var choosingGuideSources by remember { mutableStateOf(false) }
    val openGuideSources = { providerId: Long ->
        headerReturnFocus.leaveFrom(RETURN_GUIDE_SOURCES)
        onGuideSources(providerId)
    }
    val recentLabel = stringResource(R.string.category_recent_label)
    val staleSourceCount by viewModel.staleSourceCount.collectAsStateWithLifecycle()
    val processingState by viewModel.epgProcessingState.collectAsStateWithLifecycle()
    val isRefreshing =
        processingState is EpgFileManager.MultiSourceState.Pending ||
            processingState is EpgFileManager.MultiSourceState.Processing ||
            processingState is EpgFileManager.MultiSourceState.Finalizing ||
            indexState is EpgIndexState.Indexing ||
            indexState is EpgIndexState.Optimizing

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
            // The shared TV header (TV UI audit #15): the title, the icon actions on the right (no
            // source subtitle: the search covers every source of the profile); how fresh the
            // guides are goes on its own line beneath it.
            TvScreenHeader(title = stringResource(R.string.epg_browser_title)) {
                guideSourceOwners.firstOrNull()?.let { (firstLiveSource, _) ->
                    TvIconAction(
                        onClick = {
                            headerReturnFocus.leaveFrom(RETURN_TV_GUIDE)
                            onTvGuide(CategoryViewModel.RECENT_CATEGORY_ID, recentLabel, firstLiveSource)
                        },
                        icon = CinemaIcons.DateRange,
                        label = stringResource(R.string.common_tv_guide),
                        modifier = Modifier.navReturnFocusTarget(headerReturnFocus, RETURN_TV_GUIDE),
                    )
                }
                // Never disabled: a disabled button drops the focus it holds. A press while
                // refreshing does nothing; the icon turns meanwhile, as on the TV Guide.
                val refreshTurn = rememberInfiniteTransition(label = "guideSearchRefresh")
                val refreshAngle by refreshTurn.animateFloat(
                    initialValue = 0f,
                    targetValue = FULL_TURN_DEGREES,
                    animationSpec = infiniteRepeatable(tween(CinemaAnimation.fadeInDurationMs * 2, easing = LinearEasing)),
                    label = "guideSearchRefreshAngle",
                )
                TvIconAction(
                    onClick = { if (!isRefreshing) viewModel.refreshStale() },
                    icon = CinemaIcons.Refresh,
                    label = stringResource(R.string.epg_browser_refresh_stale_description),
                    iconTint = if (staleSourceCount > 0) CinemaTheme.colors.warning else null,
                    iconModifier = if (isRefreshing) Modifier.rotate(refreshAngle) else Modifier,
                )
                // The guides this search runs over, of a source that can have them.
                if (guideSourceOwners.isNotEmpty()) {
                    TvIconAction(
                        onClick = {
                            when (val choice = SourceChoice.of(guideSourceOwners)) {
                                is SourceChoice.One -> openGuideSources(choice.source.first)
                                is SourceChoice.Ask -> choosingGuideSources = true
                                SourceChoice.None -> Unit
                            }
                        },
                        icon = CinemaIcons.Tune,
                        label = stringResource(R.string.epg_sources_header),
                        modifier = Modifier.navReturnFocusTarget(headerReturnFocus, RETURN_GUIDE_SOURCES),
                    )
                }
            }
            if (choosingGuideSources) {
                TvSourceChooserDialog(
                    sources = guideSourceOwners,
                    onChosen = { providerId ->
                        choosingGuideSources = false
                        openGuideSources(providerId)
                    },
                    onDismiss = { choosingGuideSources = false },
                )
            }

            TvGuideSearchPane(
                viewModel = viewModel,
                query = query,
                onQueryChange = { query = it },
                onNavigateToPlayer = onNavigateToPlayer,
                contextName = contextName,
                headerReturnPending = headerReturnFocus.key != null,
            )
        }
    }
}

/**
 * The body of "Search the guide" under its header: how fresh the guides are, the field, the
 * "In <category> only" chip ([contextName] set), the index progress, recent searches and results.
 * The host owns the field's text ([query], [onQueryChange]); [fieldModifier] goes on the field.
 */
@Composable
internal fun TvGuideSearchPane(
    viewModel: EpgBrowserViewModel,
    query: String,
    onQueryChange: (String) -> Unit,
    onNavigateToPlayer: (EpgBrowserMatchedStream) -> Unit,
    contextName: String? = null,
    // Back is handing focus to a header button: no first-open focus or keyboard here.
    headerReturnPending: Boolean = false,
    fieldModifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val contextChannels by viewModel.contextChannels.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val indexState by viewModel.indexState.collectAsStateWithLifecycle()
    val isDevMode = viewModel.isDevMode
    val epgSearchHistory by viewModel.epgSearchHistory.collectAsStateWithLifecycle()
    val oldestIngestedAtMs by viewModel.oldestEnabledIngestedAtMs.collectAsStateWithLifecycle()
    val neverRunSourceCount by viewModel.neverRunSourceCount.collectAsStateWithLifecycle()
    val hasOffSources by viewModel.hasOffSources.collectAsStateWithLifecycle()
    val staleSourceCount by viewModel.staleSourceCount.collectAsStateWithLifecycle()
    val epgDbStats =
        when (val idx = indexState) {
            is EpgIndexState.Indexed -> {
                stringResource(
                    R.string.epg_browser_dev_stats_counts_format,
                    formatCount(idx.programmeCount),
                    formatCount(idx.channelCount),
                )
            }

            else -> {
                null
            }
        }

    val nowEpoch = rememberNowEpochSeconds()
    val scale = LocalUiScale.current

    Column(modifier = Modifier.fillMaxSize()) {
        val freshnessTone =
            if (staleSourceCount > 0 || neverRunSourceCount > 0 || oldestIngestedAtMs == 0L ||
                (oldestIngestedAtMs == null && hasOffSources)
            ) {
                TextTone.Warning
            } else {
                TextTone.Secondary
            }
        CinemaText(
            text = freshnessLabel(context, oldestIngestedAtMs, nowEpoch, staleSourceCount, neverRunSourceCount, hasOffSources),
            tone = freshnessTone,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(Spacing.md))

        when (val state = uiState) {
            is EpgBrowserViewModel.UiState.NoEpgFile -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CinemaText(
                        text = stringResource(R.string.epg_browser_no_file_message),
                        role = TextRole.Body,
                        tone = TextTone.Secondary,
                    )
                }
            }

            is EpgBrowserViewModel.UiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CinemaText(
                        text = state.message,
                        role = TextRole.Body,
                        tone = TextTone.Danger,
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
                    epgSearchHistory = epgSearchHistory,
                    onSearch = { viewModel.performSearch(it) },
                    onRemoveHistoryEntry = { viewModel.removeEpgSearchHistoryEntry(it) },
                    onClearHistory = { viewModel.clearEpgSearchHistory() },
                    onClearSearch = { viewModel.clearSearch() },
                    onNavigateToPlayer = onNavigateToPlayer,
                    contextName = contextName,
                    contextChannels = contextChannels,
                    headerReturnPending = headerReturnPending,
                    query = query,
                    onQueryChange = onQueryChange,
                    fieldModifier = fieldModifier,
                )
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
    epgSearchHistory: List<String> = emptyList(),
    onSearch: (String) -> Unit,
    onRemoveHistoryEntry: (String) -> Unit = {},
    onClearHistory: () -> Unit = {},
    onClearSearch: () -> Unit = {},
    onNavigateToPlayer: (EpgBrowserMatchedStream) -> Unit = {},
    contextName: String? = null,
    contextChannels: List<GuideChannels>? = null,
    // Back is handing focus to a header button: no first-open focus or keyboard here.
    headerReturnPending: Boolean = false,
    query: String,
    onQueryChange: (String) -> Unit,
    fieldModifier: Modifier = Modifier,
) {
    val searchFocusRequester = remember { FocusRequester() }
    val firstItemFocusRequester = remember { FocusRequester() }

    // Down from the field (or the chip under it) should reach the first recent-search chip.
    val historyFocusRequester = remember { FocusRequester() }
    // Unmatched airings (no channel in the profile's sources) stay hidden; the toggle is gone
    // until a later change gives it a home.
    val matchedOnly = true
    // Opened from a TV Guide: its channels only, until switched off (GD5). Saveable: Back from a
    // channel must find the same filtered list, or the airing it came from may have moved or gone.
    var inContextOnly by rememberSaveable { mutableStateOf(true) }
    // Back from a channel's preview lands on the airing row that opened it — see ResultsContent.
    val returnFocus = rememberNavReturnFocus()
    val scale = LocalUiScale.current
    val hasResults = (uiState as? EpgBrowserViewModel.UiState.Results)?.totalPrograms ?: 0 > 0

    // The history chips only render in the Idle state, so that is the only time `down` has a
    // target to aim at.
    val showsHistory = uiState is EpgBrowserViewModel.UiState.Idle && epgSearchHistory.isNotEmpty()

    // Entry (Part II Phase 7): the field without the keyboard when there are recent searches, so
    // Down reaches them; straight into the keyboard for a first search. Not on a return from a
    // channel — NavReturnFocusEffect lands on the airing instead.
    var editing by remember {
        mutableStateOf(
            returnFocus.key == null && !headerReturnPending && uiState is EpgBrowserViewModel.UiState.Idle && epgSearchHistory.isEmpty(),
        )
    }

    // Auto-focus logic: when results appear for the first time for a new query, focus the first item
    // (not when Back is about to hand focus to the airing that was opened).
    LaunchedEffect(uiState) {
        if (returnFocus.key == null && !headerReturnPending && uiState is EpgBrowserViewModel.UiState.Results) {
            firstItemFocusRequester.requestFocusWithRetry()
        }
    }

    // Down to the first recent search, not the "Clear all" icon a spatial search would find first;
    // only wired up while the history is on screen, since aiming `down` at an unattached requester
    // would stop Down working at all.
    val downToHistory = if (showsHistory) Modifier.focusProperties { down = historyFocusRequester } else Modifier

    Column(modifier = Modifier.fillMaxSize()) {
        TvSearchField(
            modifier = (if (contextName == null) downToHistory else Modifier).then(fieldModifier),
            query = query,
            onQueryChange = onQueryChange,
            onSearchSubmit = { onSearch(query) },
            onClear = {
                onQueryChange("")
                onClearSearch()
            },
            placeholder = stringResource(R.string.epg_browser_enter_programme_placeholder),
            focusRequester = searchFocusRequester,
            editing = editing,
            onEditingChange = { editing = it },
            restingFieldModifier = Modifier.leftToRail(),
            showClearButton = query.isNotEmpty() || hasResults,
        )

        // Opened from a TV Guide: "In <category> only", a chip under the field (TV UI audit #15). A
        // bare Material3 Checkbox draws focus through LocalIndication, a ripple — invisible without
        // a pointer on TV — so it is a selectable button. It hands Left to the navigation rail.
        if (contextName != null) {
            TvSelectableButton(
                selected = inContextOnly,
                onSelect = { inContextOnly = !inContextOnly },
                text = stringResource(R.string.epg_browser_in_category_only_format, contextName),
                modifier = Modifier.padding(top = Spacing.xs).leftToRail().then(downToHistory),
            )
        }

        // Auto-focus on screen open (not on a return from a channel)
        LaunchedEffect(Unit) {
            if (returnFocus.key == null && !headerReturnPending && !editing) searchFocusRequester.requestFocusWithRetry()
        }

        if (isDevMode && epgDbStats != null) {
            CinemaText(
                text = stringResource(R.string.epg_browser_dev_stats_format, epgDbStats),
                role = TextRole.Caption,
                tone = TextTone.Tertiary,
                modifier = Modifier.padding(top = Spacing.xs.scaled(scale)),
            )
        }

        val currentIndexState = indexState
        if (currentIndexState is EpgIndexState.Indexing || currentIndexState is EpgIndexState.Optimizing) {
            val idx = currentIndexState
            val progressText =
                if (idx is EpgIndexState.Indexing) {
                    stringResource(
                        R.string.epg_browser_indexing_progress_programmes_format,
                        idx.progressPercent,
                        formatCount(idx.programmesIndexed),
                    )
                } else {
                    stringResource(
                        R.string.epg_browser_finalizing_programmes_format,
                        formatCount((idx as EpgIndexState.Optimizing).programmeCount),
                    )
                }
            val progressValue = if (idx is EpgIndexState.Indexing) idx.progressPercent / 100f else 0.95f

            Column(modifier = Modifier.padding(top = Spacing.sm.scaled(scale))) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CinemaText(
                        text =
                            if (idx is EpgIndexState.Indexing) {
                                stringResource(
                                    R.string.epg_browser_building_index_label,
                                )
                            } else {
                                stringResource(R.string.epg_browser_optimizing_index_label)
                            },
                        role = TextRole.Label,
                        tone = TextTone.Secondary,
                    )
                    CinemaText(
                        text = progressText,
                        role = TextRole.Label,
                        tone = TextTone.Secondary,
                    )
                }
                CinemaProgressBar(
                    progress = { progressValue },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.xxs.scaled(scale)),
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
                                onQueryChange(term)
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
                        val hintText = stringResource(R.string.epg_browser_hint_search_titles)
                        CinemaText(
                            text = hintText,
                            tone = TextTone.Secondary,
                        )
                    }
                }
            }

            is EpgBrowserViewModel.UiState.IndexBusy -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CinemaText(
                        text = uiState.message(),
                        tone = TextTone.Secondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            is EpgBrowserViewModel.UiState.Searching -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    MitadyLoading(
                        role = TextRole.Display,
                        tone = TextTone.Accent,
                    )
                }
            }

            is EpgBrowserViewModel.UiState.Results -> {
                ResultsContent(
                    results = uiState,
                    nowEpoch = nowEpoch,
                    isDevMode = isDevMode,
                    matchedOnly = matchedOnly,
                    onNavigateToPlayer = onNavigateToPlayer,
                    firstItemFocusRequester = firstItemFocusRequester,
                    returnFocus = returnFocus,
                    contextName = contextName.takeIf { inContextOnly },
                    contextChannels = contextChannels,
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
            CinemaText(text = stringResource(R.string.epg_browser_recent_searches), role = TextRole.Title)
            CinemaIconButton(
                onClick = onClearAll,
                icon = {
                    Icon(
                        imageVector = CinemaIcons.Delete,
                        contentDescription = stringResource(R.string.epg_browser_clear_all_description),
                        modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
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
            val chipLook = TvCardLooks.chip()
            history.forEachIndexed { index, term ->
                TvCard(
                    onClick = { onItemClick(term) },
                    // The first recent search starts the first line: Left from it goes to the navigation
                    // rail (where the wrapped lines start isn't known here).
                    modifier =
                        when {
                            index != 0 -> Modifier
                            firstItemFocusRequester != null -> Modifier.focusRequester(firstItemFocusRequester).leftToRail()
                            else -> Modifier.leftToRail()
                        },
                    look = chipLook,
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
                            tint = CinemaTheme.colors.textSecondary,
                            modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
                        )
                        CinemaText(
                            text = term,
                            role = TextRole.Body,
                            tone = TextTone.Primary,
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
    matchedOnly: Boolean = true,
    onNavigateToPlayer: (EpgBrowserMatchedStream) -> Unit = {},
    returnFocus: NavReturnFocus,
    firstItemFocusRequester: FocusRequester? = null,
    // The TV Guide list the results are limited to (GD5), and its channels once loaded.
    contextName: String? = null,
    contextChannels: List<GuideChannels>? = null,
) {
    val scale = LocalUiScale.current

    // Filter date groups when hiding unmatched channels, then to the guide's channels when asked
    val contextFilter = contextChannels?.takeIf { contextName != null }
    val displayDateGroups =
        remember(results.dateGroups, matchedOnly, contextFilter) {
            val matched = if (matchedOnly) filterMatchedOnly(results.dateGroups) else results.dateGroups
            contextFilter?.let { filterToStreams(matched, it) } ?: matched
        }

    Column {
        // Counts, timing and search path: developer information (TV UI audit #15).
        if (isDevMode) {
            CinemaText(text = results.statsLine(), tone = TextTone.Secondary, modifier = Modifier.padding(bottom = Spacing.sm))
        }
        if (results.searchPath == EpgSearchPath.LIKE_FALLBACK) {
            CinemaText(
                text = stringResource(R.string.epg_browser_partial_results_note),
                role = TextRole.Body,
                tone = TextTone.Warning,
                modifier = Modifier.padding(bottom = Spacing.sm.scaled(scale)),
            )
        }

        if (displayDateGroups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CinemaText(
                    text = results.noResultsMessage(matchedOnly, contextName.takeIf { contextFilter != null }),
                    tone = TextTone.Secondary,
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
            val listState = rememberLazyListState()
            NavReturnFocusEffect(returnFocus, listState = listState)
            val pinnedHeaderLabel by remember {
                derivedStateOf {
                    val firstVisible = listState.firstVisibleItemIndex
                    headerIndices.lastOrNull { it.first <= firstVisible }?.second
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
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
                            val returnKeyPrefix = RETURN_AIRING_PREFIX + program.id + RETURN_AIRING_SEPARATOR
                            ProgramCard(
                                program = program,
                                nowEpoch = nowEpoch,
                                isDevMode = isDevMode,
                                onNavigateToPlayer = onNavigateToPlayer,
                                onAiringLeave = { airingIndex -> returnFocus.leaveFrom(returnKeyPrefix + airingIndex, listState) },
                                // Every airing row spans the list, so each hands Left to the navigation rail.
                                airingModifier = { airingIndex ->
                                    Modifier.navReturnFocusTarget(returnFocus, returnKeyPrefix + airingIndex).leftToRail()
                                },
                                modifier =
                                    if (isFirstItem && index == 0) {
                                        isFirstItem = false
                                        if (firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                                    } else {
                                        Modifier
                                    },
                            )
                        }
                    }
                }

                pinnedHeaderLabel?.let { label ->
                    DateHeader(
                        dateLabel = label,
                        modifier = Modifier.background(CinemaTheme.colors.surface),
                    )
                }
            }
        }
    }
}

// Key of the airing row that opened a channel: prefix + programme id + separator + airing index.
private const val RETURN_AIRING_PREFIX = "airing:"
private const val RETURN_AIRING_SEPARATOR = "#"

@Composable
private fun DateHeader(
    dateLabel: String,
    modifier: Modifier = Modifier,
) {
    val scale = LocalUiScale.current
    CinemaText(
        text = dateLabel,
        role = TextRole.Heading,
        tone = TextTone.Primary,
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
    onNavigateToPlayer: (EpgBrowserMatchedStream) -> Unit = {},
    modifier: Modifier = Modifier,
    // Called with the airing's index just before one of its rows opens a channel; airingModifier
    // decorates each row (the caller marks the one Back returns to).
    onAiringLeave: (Int) -> Unit = {},
    airingModifier: (Int) -> Modifier = { Modifier },
) {
    val scale = LocalUiScale.current
    var pendingConfirmAiring by remember { mutableStateOf<EpgBrowserAiring?>(null) }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xs.scaled(scale))
                .clip(CinemaTheme.shapes.card)
                .background(CinemaTheme.colors.surface),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md.scaled(scale)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CinemaText(
                    text = program.title,
                    role = TextRole.Heading,
                    tone = TextTone.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).bounceMarquee(),
                )
                val category = program.category
                if (category != null) {
                    GlassPanel(
                        modifier = Modifier.padding(start = Spacing.sm.scaled(scale)),
                    ) {
                        CinemaText(
                            text = category,
                            role = TextRole.Label,
                            keepCase = true,
                            tone = TextTone.Secondary,
                            modifier =
                                Modifier.padding(
                                    horizontal = Spacing.sm.scaled(scale),
                                    vertical = Spacing.xxs.scaled(scale),
                                ),
                        )
                    }
                }
            }

            val description = program.description
            if (!description.isNullOrBlank()) {
                CinemaText(
                    text = description,
                    role = TextRole.Body,
                    tone = TextTone.Secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.xs.scaled(scale)),
                )
            }

            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
            program.airings.forEachIndexed { airingIndex, airing ->
                AiringRow(
                    airing = airing,
                    nowEpoch = nowEpoch,
                    isDevMode = isDevMode,
                    onNavigateToPlayer = { matched ->
                        onAiringLeave(airingIndex)
                        onNavigateToPlayer(matched)
                    },
                    onRequestConfirmation = { pendingConfirmAiring = it },
                    modifier = airingModifier(airingIndex),
                )
            }

            val pending = pendingConfirmAiring
            val matched = pending?.matchedStream
            if (pending != null && matched != null) {
                val airingContext = LocalContext.current
                CinemaAlertDialog(
                    onDismissRequest = { pendingConfirmAiring = null },
                    title = { CinemaText(stringResource(R.string.epg_browser_watch_confirm_title), role = TextRole.Title) },
                    text = {
                        CinemaText(
                            stringResource(
                                R.string.epg_browser_watch_confirm_message,
                                formatAiringTime(airingContext, pending.startEpoch, pending.endEpoch),
                                pending.channelName,
                            ),
                            tone = TextTone.Secondary,
                        )
                    },
                    confirmButton = {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale))) {
                            CinemaButton(onClick = {
                                // A second click before the dialog leaves doesn't open the player twice (R-20).
                                if (pendingConfirmAiring != null) {
                                    pendingConfirmAiring = null
                                    onAiringLeave(program.airings.indexOf(pending))
                                    onNavigateToPlayer(matched)
                                }
                            }) {
                                CinemaText(
                                    stringResource(R.string.epg_browser_watch_now_btn),
                                    role = TextRole.Label,
                                    tone = TextTone.Inherit,
                                )
                            }

                            if (pending.startEpoch > nowEpoch && canOpenCalendar(airingContext)) {
                                CinemaButton(onClick = {
                                    pendingConfirmAiring = null
                                    openAddToCalendarEvent(
                                        context = airingContext,
                                        title = program.title,
                                        description = program.description,
                                        location = pending.channelName,
                                        startEpochSeconds = pending.startEpoch,
                                        endEpochSeconds = pending.endEpoch,
                                    )
                                }) {
                                    CinemaText(
                                        stringResource(R.string.epg_browser_add_calendar_btn),
                                        role = TextRole.Label,
                                        tone = TextTone.Inherit,
                                    )
                                }
                            }
                        }
                    },
                    dismissButton = {
                        CinemaButton(onClick = {
                            pendingConfirmAiring = null
                        }) { CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit) }
                    },
                    containerColor = CinemaTheme.colors.surface,
                )
            }
        }
    }
}

@Composable
private fun AiringRow(
    airing: EpgBrowserAiring,
    nowEpoch: Long,
    modifier: Modifier = Modifier,
    isDevMode: Boolean = false,
    onNavigateToPlayer: (EpgBrowserMatchedStream) -> Unit = {},
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
                    tint = if (isOnAir) CinemaTheme.colors.success else CinemaTheme.colors.accentSoft,
                )
            }
            CinemaText(
                text = airing.channelName,
                role = TextRole.Heading,
                tone = TextTone.Primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).bounceMarquee(),
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
                TvStatusBadge(text = badgeLabel, kind = if (isOnAir) TvStatusBadgeKind.Success else TvStatusBadgeKind.Warning)
            }
            val airingContext = LocalContext.current
            CinemaText(
                text = formatAiringTime(airingContext, airing.startEpoch, airing.endEpoch),
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

    Surface(
        onClick = {
            airing.matchedStream?.let { matched ->
                if (isOnAir) {
                    onNavigateToPlayer(matched)
                } else {
                    onRequestConfirmation(airing)
                }
            }
        },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth(),
    ) {
        rowContent()
    }
}

private const val FULL_TURN_DEGREES = 360f
