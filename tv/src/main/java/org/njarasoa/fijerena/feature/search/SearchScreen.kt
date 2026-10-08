@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Card
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardColors
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardGlow
import androidx.tv.material3.CardScale
import androidx.tv.material3.CardShape
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.asContentTypeLabel
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.LanguageBadge
import org.njarasoa.fijerena.core.ui.components.MitadyLoading
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.model.FavoriteMenuTarget
import org.njarasoa.fijerena.core.ui.model.nameAndFavoriteState
import org.njarasoa.fijerena.core.ui.navigation.SectionRoot
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel.CategorySearchResult
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel.SearchResult
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.buildGroupedSearchResults
import org.njarasoa.fijerena.core.ui.viewmodels.toggled
import org.njarasoa.fijerena.feature.category.components.FavoriteContextMenuDialog
import org.njarasoa.fijerena.ui.components.SectionRootButton
import org.njarasoa.fijerena.ui.components.TvEmptyState
import org.njarasoa.fijerena.ui.components.TvScreenHeader
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.cards.TvListRowDefaults
import org.njarasoa.fijerena.ui.components.input.NavReturnFocus
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.TvSearchField
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens

/**
 * Search screen for searching streams across all categories.
 */
@Composable
private fun localizedContentTypeLabel(contentType: String): String =
    when (contentType) {
        "ALL" -> stringResource(R.string.content_type_all_label)
        ContentType.LIVE_TV -> stringResource(R.string.provider_live_tv_label)
        ContentType.MOVIES -> stringResource(R.string.provider_movies_label)
        ContentType.TV_SHOWS -> stringResource(R.string.provider_tv_shows_label)
        else -> contentType.asContentTypeLabel()
    }

/** What the field searches, in the viewer's words (TV UI audit X8): films, shows, channels. */
@Composable
private fun searchPlaceholder(contentType: String): String =
    when (contentType) {
        ContentType.LIVE_TV -> stringResource(R.string.tv_search_placeholder_channels)
        ContentType.MOVIES -> stringResource(R.string.tv_search_placeholder_films)
        ContentType.TV_SHOWS -> stringResource(R.string.tv_search_placeholder_shows)
        else -> stringResource(R.string.tv_search_placeholder_all)
    }

@Composable
fun SearchScreen(
    contentType: String,
    onStreamSelected: (streamId: String, streamName: String, categoryId: String, contentType: String) -> Unit,
    onCategorySelected: (categoryId: String, contentType: String) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    initialQuery: String? = null,
    /** The section-root button (P6), at the end of the header; null hides it. */
    sectionRoot: SectionRoot? = null,
) {
    val context = LocalContext.current
    val viewModel: SearchViewModel =
        viewModel(
            factory =
                remember(contentType) {
                    SearchViewModelFactory(
                        context = context.applicationContext,
                        contentType = contentType,
                    )
                },
        )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()

    // Arrived from a "more like this" card: run its title straight away. The text field picks the
    // term up from the ViewModel's state on its own.
    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) viewModel.performSearch(initialQuery)
    }

    val configuration = LocalConfiguration.current
    val appSettings = remember { AppSettings(context.applicationContext) }

    // Back from a result's details (or a category) lands on that result — see SearchResultsList.
    val returnFocus = rememberNavReturnFocus()

    var favoriteMenuTarget by remember { mutableStateOf<FavoriteMenuTarget?>(null) }
    val longPressScope = rememberCoroutineScope()

    favoriteMenuTarget?.let { target ->
        FavoriteContextMenuDialog(
            target = target,
            onConfirm = {
                when (target) {
                    is FavoriteMenuTarget.Category -> {
                        viewModel.toggleFavoriteCategory(
                            target.categoryId,
                            target.categoryName,
                            target.contentType,
                            target.isFavorite,
                        )
                    }

                    is FavoriteMenuTarget.Stream -> {
                        viewModel.toggleFavorite(
                            target.itemId,
                            target.itemName,
                            target.categoryId,
                            target.contentType,
                            target.isFavorite,
                        )
                    }
                }
            },
            onDismiss = { favoriteMenuTarget = null },
            onToggleWatched =
                (target as? FavoriteMenuTarget.Stream)?.isWatched?.let { isWatched ->
                    { viewModel.toggleWatched(target.itemId, target.contentType, isWatched) }
                },
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = Spacing.tvSafeMarginHorizontal,
                        vertical = Spacing.tvSafeMarginVertical,
                    ),
        ) {
            TvScreenHeader(
                title = stringResource(R.string.common_search),
                subtitle = localizedContentTypeLabel(contentType),
            ) {
                SectionRootButton(sectionRoot)
            }

            when (val state = uiState) {
                is SearchViewModel.UiState.Loading -> {
                    LoadingView(message = state.message)
                }

                is SearchViewModel.UiState.Error -> {
                    ErrorView(state.message)
                }

                is SearchViewModel.UiState.Success -> {
                    val successState = state
                    val failedSuffix = if (successState.failedCalls > 0) " (${successState.failedCalls} failed)" else ""
                    val errorSuffix = if (successState.firstError != null) "\n${successState.firstError}" else ""
                    val devStats =
                        if (appSettings.isDevMode && successState.searchDataSize != null) {
                            "${successState.searchDataSize} fetched | ${successState.totalDuration} total | network: ${successState.networkWallDuration} wall / ${successState.networkAccumDuration} accum | ${successState.networkCalls} calls$failedSuffix$errorSuffix"
                        } else {
                            null
                        }
                    SearchContent(
                        query = successState.query,
                        categoryResults = successState.categoryResults,
                        results = successState.filteredResults,
                        excludedCountByType = successState.excludedCountByType,
                        isSearching = successState.isSearching,
                        searchProgress = successState.searchProgress ?: "",
                        devStats = devStats,
                        contentType = contentType,
                        searchHistory = searchHistory,
                        returnFocus = returnFocus,
                        // A "more like this" arrival is already searching; don't cover it with the keyboard.
                        mayOpenKeyboard = initialQuery.isNullOrBlank(),
                        onSearchSubmit = { viewModel.performSearch(it) },
                        onHistoryItemClick = { term ->
                            viewModel.performSearch(term)
                        },
                        onHistoryItemRemove = { term ->
                            viewModel.removeSearchHistoryEntry(term)
                        },
                        onClearHistory = { viewModel.clearSearchHistory() },
                        onClearSearch = { viewModel.clearSearch() },
                        onResultClick = { result ->
                            onStreamSelected(result.itemId, result.streamName, result.categoryId, result.contentType)
                        },
                        onResultLongPress = { result ->
                            // Manual watched/unwatched mark (Phase 6,
                            // docs/plans/archive/20260828_watch-state-durable-storage-plan.md). Unlike isFavorite,
                            // there is no synchronous in-memory cache for watch_state — the
                            // menu target has to wait on one suspend fetch before it opens.
                            val isWatchable =
                                result.contentType == ContentType.MOVIES || result.contentType == ContentType.TV_SHOWS
                            longPressScope.launch {
                                val isWatched = if (isWatchable) viewModel.isWatchedSuspend(result.itemId, result.contentType) else null
                                favoriteMenuTarget =
                                    FavoriteMenuTarget.Stream(
                                        itemId = result.itemId,
                                        itemName = result.streamName,
                                        categoryId = result.categoryId,
                                        contentType = result.contentType,
                                        isFavorite = viewModel.isFavorite(result.itemId, result.contentType),
                                        isWatched = isWatched,
                                    )
                            }
                        },
                        onCategoryClick = { catResult ->
                            onCategorySelected(catResult.categoryId, catResult.contentType)
                        },
                        onCategoryLongPress = { catResult ->
                            favoriteMenuTarget =
                                FavoriteMenuTarget.Category(
                                    categoryId = catResult.categoryId,
                                    categoryName = catResult.categoryName,
                                    contentType = catResult.contentType,
                                    isFavorite = viewModel.isFavoriteCategory(catResult.categoryId, catResult.contentType),
                                )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingView(message: String? = null) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(TvDimensions.iconXLarge),
                color = CinemaAccent,
            )
            Text(
                text =
                    message ?: androidx.compose.ui.res
                        .stringResource(org.njarasoa.fijerena.core.ui.R.string.search_loading_categories),
                style = MaterialTheme.typography.titleLarge,
                color = CinemaTextSecondary,
            )
        }
    }
}

@Composable
private fun ErrorView(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.padding(Spacing.xl),
        ) {
            Text(
                text = stringResource(R.string.common_error),
                style = MaterialTheme.typography.displayMedium,
                color = CinemaError,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = CinemaTextSecondary,
            )
        }
    }
}

@Composable
private fun SearchContent(
    query: String,
    categoryResults: List<CategorySearchResult>,
    results: List<SearchResult>,
    excludedCountByType: Map<String, Int>,
    isSearching: Boolean,
    searchProgress: String?,
    devStats: String?,
    contentType: String,
    searchHistory: List<String>,
    returnFocus: NavReturnFocus,
    mayOpenKeyboard: Boolean,
    onSearchSubmit: (String) -> Unit,
    onHistoryItemClick: (String) -> Unit,
    onHistoryItemRemove: (String) -> Unit,
    onClearHistory: () -> Unit,
    onClearSearch: () -> Unit,
    onResultClick: (SearchResult) -> Unit,
    onResultLongPress: (SearchResult) -> Unit,
    onCategoryClick: (CategorySearchResult) -> Unit,
    onCategoryLongPress: (CategorySearchResult) -> Unit,
) {
    val searchFocusRequester = remember { FocusRequester() }

    // Down from the search field should reach the first recent-search chip. Left to a spatial
    // focus search it lands on the "Recent Searches" header's clear-all button instead, which sits
    // between the field and the chips. Hoisted so the field can point at it; only wired up while
    // the history is actually on screen, since aiming `down` at an unattached requester would stop
    // Down working at all.
    val historyFocusRequester = remember { FocusRequester() }

    var localQuery by remember { mutableStateOf(query) }

    // Sync with incoming query only if local is empty (prevents erasing user input)
    LaunchedEffect(query) {
        if (localQuery.isEmpty() && query.isNotEmpty()) {
            localQuery = query
        }
    }

    // Results or empty state — show results whenever they exist, even if text field is cleared
    val hasResults = categoryResults.isNotEmpty() || results.isNotEmpty() || isSearching
    val showsHistory = !hasResults && query.isEmpty() && searchHistory.isNotEmpty()

    // Entry (Part II Phase 7, F-S-1/F-S-2): the field without the keyboard when there are recent
    // searches, so Down reaches them; straight into the keyboard for a first search. Not on a
    // Back from a result — NavReturnFocusEffect lands on that result instead.
    var editing by remember {
        mutableStateOf(mayOpenKeyboard && returnFocus.key == null && query.isEmpty() && !hasResults && searchHistory.isEmpty())
    }
    LaunchedEffect(Unit) {
        if (returnFocus.key == null && !editing) searchFocusRequester.requestFocusWithRetry()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TvSearchField(
            modifier =
                if (showsHistory) {
                    Modifier.focusProperties { down = historyFocusRequester }
                } else {
                    Modifier
                },
            query = localQuery,
            onQueryChange = { localQuery = it },
            onSearchSubmit = { onSearchSubmit(localQuery) },
            onClear = {
                localQuery = ""
                onClearSearch()
            },
            placeholder = searchPlaceholder(contentType),
            focusRequester = searchFocusRequester,
            editing = editing,
            onEditingChange = { editing = it },
            showClearButton = localQuery.isNotEmpty() || results.isNotEmpty() || categoryResults.isNotEmpty(),
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        if (!hasResults && query.isEmpty()) {
            if (searchHistory.isNotEmpty()) {
                SearchHistorySection(
                    history = searchHistory,
                    onItemClick = { term ->
                        localQuery = term
                        onHistoryItemClick(term)
                    },
                    onItemRemove = onHistoryItemRemove,
                    onClearAll = onClearHistory,
                    firstItemFocusRequester = historyFocusRequester,
                )
            } else {
                TvEmptyState(message = searchPlaceholder(contentType), icon = CinemaIcons.Search)
            }
        } else {
            SearchResultsList(
                categoryResults = categoryResults,
                results = results,
                excludedCountByType = excludedCountByType,
                query = query,
                queryContentType = contentType,
                isSearching = isSearching,
                searchProgress = searchProgress,
                devStats = devStats,
                returnFocus = returnFocus,
                onResultClick = onResultClick,
                onResultLongPress = onResultLongPress,
                onCategoryClick = onCategoryClick,
                onCategoryLongPress = onCategoryLongPress,
            )
        }
    }
}

@Composable
private fun SearchHistorySection(
    history: List<String>,
    onItemClick: (String) -> Unit,
    onItemRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.epg_browser_recent_searches),
                style = MaterialTheme.typography.headlineSmall,
                color = CinemaTextPrimary,
            )
            CinemaIconButton(
                onClick = onClearAll,
                icon = {
                    Icon(
                        imageVector = CinemaIcons.Delete,
                        contentDescription = stringResource(R.string.epg_browser_clear_all_description),
                        modifier = Modifier.size(TvDimensions.iconSmall),
                        tint = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary,
                    )
                },
            )
        }
        // Wraps rather than scrolling sideways: the whole history should be visible at a glance,
        // and a D-pad user should not have to scroll a rail to reach the last entry.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
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
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Icon(
                            imageVector = CinemaIcons.Search,
                            contentDescription = null,
                            tint = CinemaTextSecondary,
                            modifier = Modifier.size(TvDimensions.iconSmall),
                        )
                        Text(
                            text = term,
                            style = MaterialTheme.typography.bodyMedium,
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
private fun SearchResultsList(
    categoryResults: List<CategorySearchResult>,
    results: List<SearchResult>,
    excludedCountByType: Map<String, Int>,
    query: String,
    queryContentType: String,
    isSearching: Boolean,
    searchProgress: String?,
    devStats: String?,
    returnFocus: NavReturnFocus,
    onResultClick: (SearchResult) -> Unit,
    onResultLongPress: (SearchResult) -> Unit,
    onCategoryClick: (CategorySearchResult) -> Unit,
    onCategoryLongPress: (CategorySearchResult) -> Unit,
) {
    // Back from a result's details hands focus to that result (keyed by its LazyColumn key), at the
    // scroll position the list had when it was opened.
    val listState = rememberLazyListState()
    NavReturnFocusEffect(returnFocus, listState = listState)

    // Stable within a query's results — only add missing keys, never discard existing
    // FocusRequesters, so focus targeting survives recomposition mid-query. Keyed to
    // categoryResults/results so a new query drops the old query's requesters instead of
    // accumulating one per result id ever seen this session.
    val focusRequesters = remember(categoryResults, results) { java.util.concurrent.ConcurrentHashMap<String, FocusRequester>() }
    val firstItemFocusRequester = remember { FocusRequester() }

    var expandedGroups by rememberSaveable { mutableStateOf(setOf("LIVE_TV", "MOVIES", "TV_SHOWS")) }
    val rowStyle = searchRowStyle()

    // Auto-focus logic: when results appear for the first time for a new query, focus the first item
    // — not when Back has just rebuilt the list and NavReturnFocusEffect is about to hand focus to
    // the result that was opened.
    LaunchedEffect(categoryResults, results, isSearching) {
        if (returnFocus.key == null && !isSearching && (categoryResults.isNotEmpty() || results.isNotEmpty())) {
            firstItemFocusRequester.requestFocusWithRetry()
        }
    }

    fun toggleGroup(contentType: String) {
        expandedGroups = expandedGroups.toggled(contentType)
    }

    if (isSearching && categoryResults.isEmpty() && results.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            MitadyLoading(
                style = MaterialTheme.typography.headlineMedium,
                color = CinemaAccent,
            )
        }
    } else if (categoryResults.isEmpty() && results.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text =
                    if (queryContentType == "ALL") {
                        stringResource(R.string.search_no_results_query_format_tv, query)
                    } else {
                        stringResource(R.string.search_no_results_type_format_tv, localizedContentTypeLabel(queryContentType), query)
                    },
                style = MaterialTheme.typography.bodyLarge,
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
            )
        }
    } else {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.sm),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (searchProgress != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isSearching) {
                            MitadyLoading(
                                style = MaterialTheme.typography.bodyMedium,
                                color = CinemaAccent,
                            )
                        }
                        Text(
                            text = if (isSearching) " ($searchProgress)" else searchProgress,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CinemaAccent,
                        )
                    }
                }
            }

            if (devStats != null) {
                Text(
                    text = devStats,
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontSize = MaterialTheme.typography.labelSmall.fontSize * CinemaAlpha.textMedium,
                        ),
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textLow),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = Spacing.xs),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }

            // Pre-compute grouped results outside LazyColumn to avoid O(N×types) per recomposition
            val groupedByType =
                remember(categoryResults, results) {
                    buildGroupedSearchResults(categoryResults, results)
                }

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (queryContentType == "ALL") {
                    var isFirstItem = true
                    groupedByType.forEach { (type, typeCats, typeStreams) ->

                        if (typeCats.isNotEmpty() || typeStreams.isNotEmpty()) {
                            val isExpanded = expandedGroups.contains(type)
                            item(key = "header_$type", contentType = "header") {
                                CollapsibleHeader(
                                    title = localizedContentTypeLabel(type),
                                    count = typeCats.size + typeStreams.size,
                                    hiddenCount = excludedCountByType[type] ?: 0,
                                    isExpanded = isExpanded,
                                    onToggle = { toggleGroup(type) },
                                )
                            }

                            if (isExpanded) {
                                itemsIndexed(
                                    typeCats,
                                    key = { _, it -> "cat_${it.categoryId}_${it.contentType}" },
                                    contentType = { _, _ -> "category" },
                                ) { index, catResult ->
                                    val returnKey = "cat_${catResult.categoryId}_${catResult.contentType}"
                                    CategoryResultItem(
                                        result = catResult,
                                        rowStyle = rowStyle,
                                        onClick = {
                                            returnFocus.leaveFrom(returnKey, listState)
                                            onCategoryClick(catResult)
                                        },
                                        onLongPress = { onCategoryLongPress(catResult) },
                                        modifier =
                                            if (isFirstItem &&
                                                index == 0
                                            ) {
                                                Modifier.focusRequester(firstItemFocusRequester)
                                            } else {
                                                Modifier
                                            }.navReturnFocusTarget(returnFocus, returnKey),
                                    )
                                    if (isFirstItem && index == 0) isFirstItem = false
                                }
                                itemsIndexed(
                                    typeStreams,
                                    key = { _, it -> "stream_${it.itemId}_${it.categoryId}_${it.contentType}" },
                                    contentType = { _, _ -> "stream" },
                                ) { index, result ->
                                    val returnKey = "stream_${result.itemId}_${result.categoryId}_${result.contentType}"
                                    SearchResultItem(
                                        result = result,
                                        rowStyle = rowStyle,
                                        onClick = {
                                            returnFocus.leaveFrom(returnKey, listState)
                                            onResultClick(result)
                                        },
                                        modifier = Modifier.navReturnFocusTarget(returnFocus, returnKey),
                                        onLongPress = { onResultLongPress(result) },
                                        focusRequester =
                                            if (isFirstItem &&
                                                index == 0
                                            ) {
                                                firstItemFocusRequester
                                            } else {
                                                focusRequesters.getOrPut(result.itemId) { FocusRequester() }
                                            },
                                    )
                                    if (isFirstItem && index == 0) isFirstItem = false
                                }
                            }
                        }
                    }
                } else {
                    // Specific content type search - no need for collapsible groups
                    if (categoryResults.isNotEmpty()) {
                        item(key = "category_header", contentType = "header") {
                            SearchSectionHeader(
                                title = stringResource(R.string.search_tab_categories),
                                count = categoryResults.size,
                                hiddenCount = 0,
                            )
                        }
                        itemsIndexed(
                            categoryResults,
                            key = { _, it -> "cat_${it.categoryId}_${it.contentType}" },
                            contentType = { _, _ -> "category" },
                        ) { index, catResult ->
                            val returnKey = "cat_${catResult.categoryId}_${catResult.contentType}"
                            CategoryResultItem(
                                result = catResult,
                                rowStyle = rowStyle,
                                onClick = {
                                    returnFocus.leaveFrom(returnKey, listState)
                                    onCategoryClick(catResult)
                                },
                                onLongPress = { onCategoryLongPress(catResult) },
                                modifier =
                                    (if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                                        .navReturnFocusTarget(returnFocus, returnKey),
                            )
                        }
                    }

                    if (results.isNotEmpty()) {
                        item(key = "stream_header", contentType = "header") {
                            SearchSectionHeader(
                                title = localizedContentTypeLabel(queryContentType),
                                count = results.size,
                                hiddenCount = excludedCountByType[queryContentType] ?: 0,
                            )
                        }
                        itemsIndexed(results, key = {
                            _,
                            it,
                            ->
                            "${it.itemId}_${it.categoryId}"
                        }, contentType = { _, _ -> "stream" }) { index, result ->
                            val returnKey = "${result.itemId}_${result.categoryId}"
                            SearchResultItem(
                                result = result,
                                rowStyle = rowStyle,
                                onClick = {
                                    returnFocus.leaveFrom(returnKey, listState)
                                    onResultClick(result)
                                },
                                modifier = Modifier.navReturnFocusTarget(returnFocus, returnKey),
                                onLongPress = { onResultLongPress(result) },
                                focusRequester =
                                    if (categoryResults.isEmpty() &&
                                        index == 0
                                    ) {
                                        firstItemFocusRequester
                                    } else {
                                        focusRequesters.getOrPut(result.itemId) { FocusRequester() }
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "12 results" or "12 results · 3 hidden" when the provider's category filters hid matches. */
@Composable
private fun searchCountLabel(
    count: Int,
    hiddenCount: Int,
): String {
    val label = stringResource(R.string.search_results_count_format, count)
    return if (hiddenCount > 0) {
        label + " · " + stringResource(R.string.search_results_hidden_format, hiddenCount)
    } else {
        label
    }
}

@Composable
private fun SearchSectionHeader(
    title: String,
    count: Int,
    hiddenCount: Int,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = CinemaTextPrimary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = searchCountLabel(count, hiddenCount),
            style = MaterialTheme.typography.bodyMedium,
            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
        )
    }
}

@Composable
private fun CollapsibleHeader(
    title: String,
    count: Int,
    hiddenCount: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        onClick = onToggle,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xs),
        // A section title that folds its group: no fill at rest, the content rows' focus look
        // (light lift, white outline) when focused.
        colors =
            CardDefaults.colors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                contentColor = CinemaTextPrimary,
                focusedContainerColor = TvFocusTokens.focusedContainer,
                focusedContentColor = CinemaTextPrimary,
            ),
        shape = TvListRowDefaults.shape(),
        border = TvListRowDefaults.border(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = CinemaTextPrimary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = searchCountLabel(count, hiddenCount),
                style = MaterialTheme.typography.bodyMedium,
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                modifier = Modifier.padding(end = Spacing.sm),
            )
            Icon(
                imageVector = if (isExpanded) CinemaIcons.KeyboardArrowUp else CinemaIcons.KeyboardArrowDown,
                contentDescription = if (isExpanded) stringResource(R.string.common_collapse) else stringResource(R.string.common_expand),
                tint = CinemaTextSecondary,
                modifier = Modifier.size(TvDimensions.iconMedium),
            )
        }
    }
}

/**
 * The content rows' focus look ([TvListRowDefaults]: light lift, white outline, text stays white),
 * built once per results list rather than once per row.
 */
@Immutable
private data class SearchRowStyle(
    val colors: CardColors,
    val border: CardBorder,
    val cardScale: CardScale,
    val glow: CardGlow,
    val shape: CardShape,
)

@Composable
private fun searchRowStyle(): SearchRowStyle =
    SearchRowStyle(
        colors = TvListRowDefaults.colors(),
        border = TvListRowDefaults.border(),
        cardScale = TvListRowDefaults.scale(),
        glow = TvListRowDefaults.glow(),
        shape = TvListRowDefaults.shape(),
    )

@Composable
private fun CategoryResultItem(
    result: CategorySearchResult,
    rowStyle: SearchRowStyle,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier =
            modifier
                .padding(horizontal = Spacing.md)
                .fillMaxWidth()
                .height(TvDimensions.cardHeight)
                .tvLongPress(onLongPress),
        colors = rowStyle.colors,
        border = rowStyle.border,
        scale = rowStyle.cardScale,
        glow = rowStyle.glow,
        shape = rowStyle.shape,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = result.categoryName,
                style = MaterialTheme.typography.titleMedium,
                color = CinemaTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchResultItem(
    result: SearchResult,
    rowStyle: SearchRowStyle,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
    onLongPress: () -> Unit = {},
) {
    Card(
        onClick = onClick,
        modifier =
            modifier
                .padding(horizontal = Spacing.md)
                .fillMaxWidth()
                .height(TvDimensions.cardHeight)
                .tvLongPress(onLongPress)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        colors = rowStyle.colors,
        border = rowStyle.border,
        scale = rowStyle.cardScale,
        glow = rowStyle.glow,
        shape = rowStyle.shape,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A channel's logo is drawn whole (see StreamList). 16:9 at the row's height, not a
            // poster-width strip squashed into it (TV UI audit #14).
            val isChannel = result.contentType == ContentType.LIVE_TV
            CinemaThumbnail(
                url = result.thumbnailUrl,
                fallbackLetter = result.streamName.firstOrNull(),
                contentType = if (isChannel) ThumbnailContentType.LIVE_TV else ThumbnailContentType.DEFAULT,
                overlayGradient = !isChannel,
                modifier = Modifier.fillMaxHeight().aspectRatio(16f / 9f),
            )
            Column(
                verticalArrangement = Arrangement.Center,
            ) {
                // Same title as the browse lists: the provider's "EN - " / "4K-NF - " tag as a badge.
                val parsedTitle = remember(result.streamName) { parseDisplayTitle(result.streamName) }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    parsedTitle.badge?.let { LanguageBadge(it) }
                    Text(
                        text = parsedTitle.title.ifBlank { result.streamName },
                        style = MaterialTheme.typography.titleMedium,
                        color = CinemaTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // The category it is in, as a plain secondary line ("kids' movies", not "Category: kids' movies").
                Text(
                    text = result.categoryName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CinemaTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * TV-specific long press modifier for D-pad Center key.
 * Triggers onKeyUp ONLY if a long-press was detected via onKeyDown repeat counts.
 */
private fun Modifier.tvLongPress(onLongPress: () -> Unit): Modifier =
    composed {
        var longPressDetected by remember { mutableStateOf(false) }

        onPreviewKeyEvent { event ->
            val isDpadCenter =
                event.key == Key.DirectionCenter ||
                    event.key == Key.Enter ||
                    event.key == Key.NumPadEnter

            if (isDpadCenter &&
                event.type == KeyEventType.KeyDown &&
                event.nativeKeyEvent.repeatCount > 0 &&
                event.nativeKeyEvent.isLongPress &&
                !longPressDetected
            ) {
                longPressDetected = true
                true
            } else if (isDpadCenter && event.type == KeyEventType.KeyDown && longPressDetected) {
                true
            } else if (isDpadCenter && event.type == KeyEventType.KeyUp && longPressDetected) {
                longPressDetected = false
                onLongPress()
                true
            } else {
                false
            }
        }
    }
