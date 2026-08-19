@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.asContentTypeLabel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.viewmodels.buildGroupedSearchResults
import org.njarasoa.fijerena.core.ui.viewmodels.toggled
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.model.FavoriteMenuTarget
import org.njarasoa.fijerena.core.ui.model.nameAndFavoriteState
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel.CategorySearchResult
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel.SearchResult
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModelFactory
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.core.ui.components.MitadyLoading
import org.njarasoa.fijerena.ui.components.TvSearchTextField
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

/**
 * Search screen for searching streams across all categories.
 *
 * Features:
 * - Search input field at top
 * - Real-time filtered results below
 * - D-pad navigation between search field and results
 * - TV-optimized with 5% overscan padding
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

@Composable
fun SearchScreen(
    contentType: String,
    onStreamSelected: (streamId: String, streamName: String, categoryId: String, contentType: String) -> Unit,
    onCategorySelected: (categoryId: String, contentType: String) -> Unit = { _, _ -> },
    onBack: () -> Unit,
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
    val configuration = LocalConfiguration.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val uiScale by remember { mutableStateOf(appSettings.uiScale) }

    // Favorite long-press state
    var favoriteMenuTarget by remember { mutableStateOf<FavoriteMenuTarget?>(null) }

    favoriteMenuTarget?.let { target ->
        SearchFavoriteDialog(
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
        )
    }

    CompositionLocalProvider(LocalUiScale provides uiScale) {
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
                // Header with back button
                HeaderRow(contentType = contentType)

                Spacer(modifier = Modifier.height(Spacing.lg))

                when (val state = uiState) {
                    is SearchViewModel.UiState.Loading -> LoadingView(message = state.message)
                    is SearchViewModel.UiState.Error -> ErrorView(state.message)
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
                                favoriteMenuTarget =
                                    FavoriteMenuTarget.Stream(
                                        itemId = result.itemId,
                                        itemName = result.streamName,
                                        categoryId = result.categoryId,
                                        contentType = result.contentType,
                                        isFavorite = viewModel.isFavorite(result.itemId, result.contentType),
                                    )
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
    } // CompositionLocalProvider
}

@Composable
private fun HeaderRow(contentType: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = stringResource(R.string.common_search),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = localizedContentTypeLabel(contentType),
                style = MaterialTheme.typography.titleMedium,
                color = CinemaAccent,
            )
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
                text = message ?: androidx.compose.ui.res.stringResource(org.njarasoa.fijerena.core.ui.R.string.search_loading_categories),
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
    // Local state for text field - manages user input independently
    var localQuery by remember { mutableStateOf(query) }

    // Sync with incoming query only if local is empty (prevents erasing user input)
    LaunchedEffect(query) {
        if (localQuery.isEmpty() && query.isNotEmpty()) {
            localQuery = query
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search field
        TvSearchTextField(
            query = localQuery,
            onQueryChange = { localQuery = it },
            onSearchSubmit = { onSearchSubmit(localQuery) },
            onClear = {
                localQuery = ""
                onClearSearch()
            },
            placeholder = stringResource(R.string.search_stream_name_placeholder),
            focusRequester = searchFocusRequester,
            showClearButton = localQuery.isNotEmpty() || results.isNotEmpty() || categoryResults.isNotEmpty(),
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        // Results or empty state — show results whenever they exist, even if text field is cleared
        val hasResults = categoryResults.isNotEmpty() || results.isNotEmpty() || isSearching
        if (!hasResults && query.isEmpty()) {
            if (searchHistory.isNotEmpty()) {
                val historyFocusRequester = remember { FocusRequester() }
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.search_categories_streams_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                    )
                }
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
                style = MaterialTheme.typography.titleMedium,
                color = CinemaTextSecondary,
            )
            CinemaIconButton(
                onClick = onClearAll,
                icon = {
                    Icon(
                        imageVector = CinemaIcons.Delete,
                        contentDescription = stringResource(R.string.epg_browser_clear_all_description),
                        modifier = Modifier.size(TvDimensions.iconSmall),
                        tint = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
                    )
                },
            )
        }
        TvLazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            itemsIndexed(history) { index, term ->
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
    onResultClick: (SearchResult) -> Unit,
    onResultLongPress: (SearchResult) -> Unit,
    onCategoryClick: (CategorySearchResult) -> Unit,
    onCategoryLongPress: (CategorySearchResult) -> Unit,
) {
    // Stable within a query's results — only add missing keys, never discard existing
    // FocusRequesters, so focus targeting survives recomposition mid-query. Keyed to
    // categoryResults/results so a new query drops the old query's requesters instead of
    // accumulating one per result id ever seen this session.
    val focusRequesters = remember(categoryResults, results) { mutableMapOf<String, FocusRequester>() }
    val firstItemFocusRequester = remember { FocusRequester() }

    var expandedGroups by rememberSaveable { mutableStateOf(setOf("LIVE_TV", "MOVIES", "TV_SHOWS")) }

    // Auto-focus logic: when results appear for the first time for a new query, focus the first item
    LaunchedEffect(categoryResults, results, isSearching) {
        if (!isSearching && (categoryResults.isNotEmpty() || results.isNotEmpty())) {
            try {
                firstItemFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    fun toggleGroup(contentType: String) {
        expandedGroups = expandedGroups.toggled(contentType)
    }

    if (isSearching && categoryResults.isEmpty() && results.isEmpty()) {
        // Show loading state while searching
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
        // No results found after search completed
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

            // Pre-compute grouped results outside TvLazyColumn to avoid O(N×types) per recomposition
            val groupedByType =
                remember(categoryResults, results) {
                    buildGroupedSearchResults(categoryResults, results)
                }

            TvLazyColumn(
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
                                // Show categories for this type
                                itemsIndexed(
                                    typeCats,
                                    key = { _, it -> "cat_${it.categoryId}_${it.contentType}" },
                                    contentType = { _, _ -> "category" },
                                ) { index, catResult ->
                                    CategoryResultItem(
                                        result = catResult,
                                        onClick = { onCategoryClick(catResult) },
                                        onLongPress = { onCategoryLongPress(catResult) },
                                        modifier = if (isFirstItem && index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                                    )
                                    if (isFirstItem && index == 0) isFirstItem = false
                                }
                                // Show streams for this type
                                itemsIndexed(
                                    typeStreams,
                                    key = { _, it -> "stream_${it.itemId}_${it.categoryId}_${it.contentType}" },
                                    contentType = { _, _ -> "stream" },
                                ) { index, result ->
                                    SearchResultItem(
                                        result = result,
                                        onClick = { onResultClick(result) },
                                        onLongPress = { onResultLongPress(result) },
                                        focusRequester = if (isFirstItem && index == 0) firstItemFocusRequester else focusRequesters.getOrPut(result.itemId) { FocusRequester() },
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
                            CategoryResultItem(
                                result = catResult,
                                onClick = { onCategoryClick(catResult) },
                                onLongPress = { onCategoryLongPress(catResult) },
                                modifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                            )
                        }
                    }

                    if (results.isNotEmpty()) {
                        item(key = "stream_header", contentType = "header") {
                            SearchSectionHeader(
                                title = stringResource(R.string.search_tab_streams),
                                count = results.size,
                                hiddenCount = excludedCountByType[queryContentType] ?: 0,
                            )
                        }
                        itemsIndexed(results, key = { _, it -> "${it.itemId}_${it.categoryId}" }, contentType = { _, _ -> "stream" }) { index, result ->
                            SearchResultItem(
                                result = result,
                                onClick = { onResultClick(result) },
                                onLongPress = { onResultLongPress(result) },
                                focusRequester = if (categoryResults.isEmpty() && index == 0) firstItemFocusRequester else focusRequesters.getOrPut(result.itemId) { FocusRequester() },
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
            style = MaterialTheme.typography.titleMedium,
            color = CinemaAccent,
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
        colors =
            CardDefaults.colors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.focusedTint),
            ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CornerRadius.medium)),
        border =
            CardDefaults.border(
                focusedBorder =
                    Border(
                        border =
                            androidx.compose.foundation.BorderStroke(
                                TvDimensions.borderFocused,
                                CinemaAccent,
                            ),
                    ),
            ),
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
                style = MaterialTheme.typography.titleMedium,
                color = CinemaAccent,
                fontWeight = FontWeight.Bold,
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
                tint = CinemaAccent,
                modifier = Modifier.size(TvDimensions.iconSmall),
            )
        }
    }
}

@Composable
private fun CategoryResultItem(
    result: CategorySearchResult,
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
        colors =
            CardDefaults.colors(
                containerColor = CinemaAccent.copy(alpha = CinemaAlpha.tint),
                contentColor = CinemaTextPrimary,
                focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.glassBorder),
                focusedContentColor = CinemaTextPrimary,
            ),
        shape =
            CardDefaults.shape(
                shape =
                    androidx.compose.foundation.shape
                        .RoundedCornerShape(CornerRadius.medium),
            ),
        scale =
            CardDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScaleContent,
                pressedScale = TvFocusTokens.pressedScaleSubtle,
            ),
        glow =
            CardDefaults.glow(
                focusedGlow =
                    androidx.tv.material3.Glow(
                        elevationColor = CinemaAccent.copy(alpha = CinemaAlpha.cardElevationShadow),
                        elevation = TvFocusTokens.focusShadowElevation,
                    ),
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Accent gradient strip on left edge
            Box(
                modifier =
                    Modifier
                        .width(TvDimensions.borderFocused)
                        .height(TvDimensions.cardHeight)
                        .padding(vertical = Spacing.sm)
                        .then(
                            Modifier.fillMaxHeight(),
                        ),
            )
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
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    focusRequester: FocusRequester?,
) {
    Card(
        onClick = onClick,
        modifier =
            Modifier
                .padding(horizontal = Spacing.md)
                .fillMaxWidth()
                .height(TvDimensions.cardHeight)
                .tvLongPress(onLongPress)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        colors =
            CardDefaults.colors(
                containerColor = CinemaSurface,
                contentColor = CinemaTextPrimary,
                focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.glassBorder),
                focusedContentColor = CinemaTextPrimary,
            ),
        shape =
            CardDefaults.shape(
                shape =
                    androidx.compose.foundation.shape
                        .RoundedCornerShape(CornerRadius.medium),
            ),
        scale =
            CardDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScaleContent,
                pressedScale = TvFocusTokens.pressedScaleSubtle,
            ),
        glow =
            CardDefaults.glow(
                focusedGlow =
                    androidx.tv.material3.Glow(
                        elevationColor = CinemaAccent.copy(alpha = CinemaAlpha.cardElevationShadow),
                        elevation = TvFocusTokens.focusShadowElevation,
                    ),
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Poster thumbnail
            CinemaThumbnail(
                url = result.thumbnailUrl,
                fallbackLetter = result.streamName.firstOrNull(),
                contentType = ThumbnailContentType.DEFAULT,
                modifier =
                    Modifier.size(
                        width = TvDimensions.posterWidth,
                        height = TvDimensions.posterHeight,
                    ),
            )
            Column(
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = result.streamName,
                    style = MaterialTheme.typography.titleMedium,
                    color = CinemaTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.search_result_category_format, result.categoryName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CinemaTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun SearchFavoriteDialog(
    target: FavoriteMenuTarget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (itemName, isFavorite) = target.nameAndFavoriteState()
    val actionText = if (isFavorite) stringResource(R.string.favorite_remove) else stringResource(R.string.favorite_add)

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            androidx.tv.material3.Text(
                text = itemName,
                style = MaterialTheme.typography.titleMedium,
                color = CinemaTextPrimary,
                maxLines = 2,
            )
        },
        text = null,
        confirmButton = {
            CinemaPrimaryButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                text = actionText,
            )
        },
        dismissButton = {
            CinemaSecondaryButton(
                onClick = onDismiss,
                text = stringResource(R.string.common_cancel),
            )
        },
        containerColor = CinemaSurface,
        titleContentColor = CinemaTextPrimary,
        textContentColor = CinemaTextSecondary,
    )
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
